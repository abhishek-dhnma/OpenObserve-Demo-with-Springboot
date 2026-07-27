package com.example.amzstore.service;

import com.example.amzstore.dto.CheckoutRequest;
import com.example.amzstore.exception.DatabaseConnectionTimeoutException;
import com.example.amzstore.model.CartItem;
import com.example.amzstore.model.Order;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.ScopedSpan;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final ProductService productService;
    private final Tracer tracer;
    private final RestTemplate restTemplate;
    private final Counter successfulOrdersCounter;
    private final Counter failedOrdersCounter;
    private final Timer checkoutTimer;

    private final Map<String, Order> orderRepository = new ConcurrentHashMap<>();

    private final String inventoryServiceUrl = "http://localhost:8081/api/inventory";
    private final String paymentServiceUrl = "http://localhost:8082/api/payment";
    private final String fulfillmentServiceUrl = "http://localhost:8083/api/fulfillment";

    public Order processCheckout(CheckoutRequest request) {
        return checkoutTimer.record(() -> {
            String mainTraceId = tracer.currentSpan() != null ? tracer.currentSpan().context().traceId() : UUID.randomUUID().toString();
            String orderId = "ORD-" + System.currentTimeMillis();
            String customerEmail = request.getCustomerEmail() != null ? request.getCustomerEmail() : "customer@example.com";
            String failureType = request.getPaymentMethod() != null && request.isSimulateFailure() ? request.getPaymentMethod() : "NONE";

            Span.current().setAttribute("order.id", orderId);
            Span.current().setAttribute("customer.email", customerEmail);
            Span.current().setAttribute("checkout.failure_type", failureType);
            Span.current().addEvent("Checkout pipeline initiated");

            log.info("[order-service] Initiating order checkout pipeline [OrderID: {}, Customer: {}, FailureType: {}]",
                    orderId, customerEmail, failureType);

            // 1. AuthService (Internal Session Validation)
            validateCustomerSession(customerEmail, orderId);

            // 2. CartService (Cart Calculation)
            BigDecimal subtotal = fetchCartDetails(request.getItems(), orderId);

            // 3. HTTP Call to standalone inventory-service (Port 8081)
            boolean inventorySuccess = reserveInventoryStock(request.getItems(), orderId, "INVENTORY".equalsIgnoreCase(failureType));
            if (!inventorySuccess) {
                failedOrdersCounter.increment();
                Span.current().addEvent("Pipeline halted: Inventory allocation error");
                return createFailedOrder(orderId, customerEmail, request.getItems(), subtotal, mainTraceId,
                        "InventoryOutOfStockException: Stock allocation lock failed / DB Row lock timeout (500)");
            }

            // 4. PricingService
            BigDecimal finalAmount = calculateTaxesAndDiscounts(subtotal, orderId);

            // 5. FraudDetectionService
            evaluateFraudRiskScore(customerEmail, finalAmount, orderId);

            // 6. HTTP Call to standalone payment-service (Port 8082)
            boolean paymentSuccess = authorizePayment(orderId, finalAmount, "PAYMENT".equalsIgnoreCase(failureType) || request.isSimulateFailure());
            if (!paymentSuccess) {
                failedOrdersCounter.increment();
                Span.current().addEvent("Pipeline halted: Payment card decline");
                return createFailedOrder(orderId, customerEmail, request.getItems(), finalAmount, mainTraceId,
                        "PaymentGatewayDeclinedException: Card authorization declined by issuing bank (Code 402)");
            }

            // 7. HTTP Call to standalone fulfillment-service (Port 8083)
            String trackingId = createShippingLabel(orderId, customerEmail, "SHIPPING".equalsIgnoreCase(failureType));
            if (trackingId == null) {
                failedOrdersCounter.increment();
                Span.current().addEvent("Pipeline halted: Logistics carrier API timeout");
                return createFailedOrder(orderId, customerEmail, request.getItems(), finalAmount, mainTraceId,
                        "CarrierServiceUnavailableException: Carrier API address validation timeout (FedEx 503)");
            }

            // 8. NotificationService
            sendOrderConfirmationEmail(customerEmail, orderId, finalAmount);

            // 9. Database Persistence (Can simulate DATABASE connection timeout exception)
            boolean dbSuccess = commitOrderTransaction(orderId, finalAmount, request.getItems(), "DATABASE".equalsIgnoreCase(failureType));
            if (!dbSuccess) {
                failedOrdersCounter.increment();
                Span.current().addEvent("Pipeline halted: Database pool connection timeout");
                return createFailedOrder(orderId, customerEmail, request.getItems(), finalAmount, mainTraceId,
                        "DatabaseConnectionTimeoutException: PostgreSQL Connection Pool Timeout (504)");
            }

            successfulOrdersCounter.increment();
            Span.current().addEvent("Checkout pipeline completed successfully");

            Order successfulOrder = Order.builder()
                    .orderId(orderId)
                    .customerEmail(customerEmail)
                    .items(request.getItems())
                    .totalAmount(finalAmount)
                    .status("PAID")
                    .traceId(mainTraceId)
                    .createdAt(LocalDateTime.now())
                    .build();

            orderRepository.put(orderId, successfulOrder);
            log.info("[order-service] Order pipeline completed! OrderID: {}, Total: ${}, TrackingID: {}, TraceID: {}",
                    orderId, finalAmount, trackingId, mainTraceId);

            return successfulOrder;
        });
    }

    private Order createFailedOrder(String orderId, String email, List<CartItem> items, BigDecimal amount, String traceId, String reason) {
        log.error("[order-service] Pipeline halted for OrderID: {}. Reason: {}", orderId, reason);
        Order failedOrder = Order.builder()
                .orderId(orderId)
                .customerEmail(email)
                .items(items)
                .totalAmount(amount)
                .status("FAILED")
                .traceId(traceId)
                .createdAt(LocalDateTime.now())
                .failureReason(reason)
                .build();
        orderRepository.put(orderId, failedOrder);
        return failedOrder;
    }

    private void validateCustomerSession(String email, String orderId) {
        ScopedSpan span = tracer.startScopedSpan("AuthService :: validateCustomerSession");
        try {
            span.tag("peer.service", "auth-service");
            span.tag("order.id", orderId);
            span.tag("customer.email", email);
            Span.current().addEvent("Verifying OAuth2 JWT session token");
            log.info("[order-service] Validating session token for customer: {}", email);
            Thread.sleep(45);
            Span.current().setStatus(StatusCode.OK, "AuthService: Customer session token validated successfully");
        } catch (InterruptedException ignored) {
        } finally {
            span.end();
        }
    }

    private BigDecimal fetchCartDetails(List<CartItem> items, String orderId) {
        ScopedSpan span = tracer.startScopedSpan("CartService :: fetchCartDetails");
        try {
            span.tag("peer.service", "cart-service");
            span.tag("order.id", orderId);
            span.tag("cart.item_count", String.valueOf(items.size()));
            Span.current().addEvent("Calculating cart item quantities and unit prices");
            log.info("[order-service] Fetching item details for {} items", items.size());
            Thread.sleep(60);

            BigDecimal subtotal = items.stream()
                    .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Span.current().setStatus(StatusCode.OK, "CartService: Retrieved item pricing and cart weights");
            return subtotal;
        } catch (InterruptedException e) {
            return BigDecimal.ZERO;
        } finally {
            span.end();
        }
    }

    private boolean reserveInventoryStock(List<CartItem> items, String orderId, boolean simulateFailure) {
        ScopedSpan span = tracer.startScopedSpan("HTTP POST -> inventory-service");
        try {
            span.tag("peer.service", "inventory-service");
            span.tag("net.peer.name", "inventory-service");
            span.tag("net.peer.port", "8081");
            span.tag("http.method", "POST");
            span.tag("order.id", orderId);

            String url = inventoryServiceUrl + "/reserve?simulateFailure=" + simulateFailure + "&orderId=" + orderId;
            span.tag("http.url", url);

            Span.current().addEvent("Sending HTTP POST reservation to inventory-service (Port 8081)");
            restTemplate.postForEntity(url, null, Map.class);

            for (CartItem item : items) {
                var productOpt = productService.getProductById(item.getProductId());
                if (productOpt.isPresent()) {
                    productService.deductStock(item.getProductId(), item.getQuantity());
                }
            }

            Span.current().setStatus(StatusCode.OK, "HTTP POST to inventory-service (Port 8081) succeeded");
            return true;
        } catch (Exception e) {
            span.error(e);
            Span.current().recordException(e);
            Span.current().setStatus(StatusCode.ERROR, "InventoryOutOfStockException: DB Row lock timeout: " + e.getMessage());
            span.tag("error.code", "INVENTORY_ALLOCATION_FAILED_500");
            return false;
        } finally {
            span.end();
        }
    }

    private BigDecimal calculateTaxesAndDiscounts(BigDecimal subtotal, String orderId) {
        ScopedSpan span = tracer.startScopedSpan("PricingService :: calculateTaxesAndDiscounts");
        try {
            span.tag("peer.service", "pricing-service");
            span.tag("order.id", orderId);
            span.tag("pricing.tax_rate", "8.0%");
            Span.current().addEvent("Applying regional sales tax and item coupon discounts");
            log.info("[order-service] Calculating sales tax (8%) for subtotal: ${}", subtotal);
            Thread.sleep(35);
            BigDecimal total = subtotal.multiply(new BigDecimal("1.08")).setScale(2, RoundingMode.HALF_UP);
            Span.current().setStatus(StatusCode.OK, "PricingService: Calculated regional sales tax and item discounts");
            return total;
        } catch (InterruptedException e) {
            return subtotal;
        } finally {
            span.end();
        }
    }

    private void evaluateFraudRiskScore(String email, BigDecimal amount, String orderId) {
        ScopedSpan span = tracer.startScopedSpan("FraudDetectionService :: evaluateRiskScore");
        try {
            span.tag("peer.service", "fraud-detection-service");
            span.tag("order.id", orderId);
            span.tag("risk.score", "0.02 (LOW)");
            Span.current().addEvent("Executing ML Risk Scoring Model v2.4");
            log.info("[order-service] ML model evaluated transaction risk for {}: 0.02 (APPROVED)", email);
            Thread.sleep(80);
            Span.current().setStatus(StatusCode.OK, "FraudDetectionService: Transaction risk score 0.02 (Low / Approved)");
        } catch (InterruptedException ignored) {
        } finally {
            span.end();
        }
    }

    private boolean authorizePayment(String orderId, BigDecimal amount, boolean simulateFailure) {
        ScopedSpan span = tracer.startScopedSpan("HTTP POST -> payment-service");
        try {
            span.tag("peer.service", "payment-service");
            span.tag("net.peer.name", "payment-service");
            span.tag("net.peer.port", "8082");
            span.tag("http.method", "POST");
            span.tag("order.id", orderId);
            span.tag("payment.amount", amount.toString());
            span.tag("payment.gateway", "Stripe API v3");

            String url = paymentServiceUrl + "/authorize?simulateFailure=" + simulateFailure + "&amount=" + amount + "&orderId=" + orderId;
            span.tag("http.url", url);

            Span.current().addEvent("Sending HTTP POST authorization to payment-service (Port 8082)");
            restTemplate.postForEntity(url, null, Map.class);

            Span.current().setStatus(StatusCode.OK, "HTTP POST to payment-service (Port 8082) succeeded");
            return true;
        } catch (Exception e) {
            span.error(e);
            Span.current().recordException(e);
            Span.current().setStatus(StatusCode.ERROR, "PaymentGatewayDeclinedException: Card declined by issuing bank (Code 402)");
            span.tag("error.code", "PAYMENT_DECLINED_402");
            return false;
        } finally {
            span.end();
        }
    }

    private String createShippingLabel(String orderId, String email, boolean simulateFailure) {
        ScopedSpan span = tracer.startScopedSpan("HTTP POST -> fulfillment-service");
        try {
            span.tag("peer.service", "fulfillment-service");
            span.tag("net.peer.name", "fulfillment-service");
            span.tag("net.peer.port", "8083");
            span.tag("http.method", "POST");
            span.tag("order.id", orderId);
            span.tag("logistics.carrier", "FedEx Express");

            String url = fulfillmentServiceUrl + "/ship?simulateFailure=" + simulateFailure + "&orderId=" + orderId;
            span.tag("http.url", url);

            Span.current().addEvent("Sending HTTP POST shipping label creation to fulfillment-service (Port 8083)");
            var response = restTemplate.postForEntity(url, null, Map.class);

            String trackingId = response.getBody() != null ? (String) response.getBody().get("trackingId") : "TRK-LOCAL";
            Span.current().setStatus(StatusCode.OK, "HTTP POST to fulfillment-service (Port 8083) succeeded: " + trackingId);
            return trackingId;
        } catch (Exception e) {
            span.error(e);
            Span.current().recordException(e);
            Span.current().setStatus(StatusCode.ERROR, "CarrierServiceUnavailableException: FedEx API timeout (Code 503)");
            span.tag("error.code", "CARRIER_API_TIMEOUT_503");
            return null;
        } finally {
            span.end();
        }
    }

    private void sendOrderConfirmationEmail(String email, String orderId, BigDecimal amount) {
        ScopedSpan span = tracer.startScopedSpan("NotificationService :: sendOrderConfirmationEmail");
        try {
            span.tag("peer.service", "notification-service");
            span.tag("order.id", orderId);
            span.tag("notification.type", "EMAIL");
            Span.current().addEvent("Dispatching email notification via SendGrid SMTP");
            log.info("[order-service] Sent order confirmation email to: {}", email);
            Thread.sleep(90);
            Span.current().setStatus(StatusCode.OK, "NotificationService: Sent order confirmation email to " + email);
        } catch (InterruptedException ignored) {
        } finally {
            span.end();
        }
    }

    private boolean commitOrderTransaction(String orderId, BigDecimal amount, List<CartItem> items, boolean simulateFailure) {
        ScopedSpan span = tracer.startScopedSpan("DatabaseService :: commitOrderTransaction");
        try {
            span.tag("peer.service", "postgresql-database");
            span.tag("order.id", orderId);
            span.tag("db.type", "postgresql");
            span.tag("db.statement", "INSERT INTO orders VALUES (?, ?, ?)");

            if (simulateFailure) {
                DatabaseConnectionTimeoutException ex = new DatabaseConnectionTimeoutException("PostgreSQL Connection Pool Timeout: Active connections exceeded maximum limit (100/100)");
                log.error("[database-service] Database transaction commit failed for OrderID: {}! HikariCP Pool Exhausted.", orderId, ex);
                span.error(ex);
                Span.current().recordException(ex);
                Span.current().setStatus(StatusCode.ERROR, ex.getMessage());
                span.tag("error.code", "DATABASE_TIMEOUT_504");
                return false;
            }

            Span.current().addEvent("Committing ACID transaction in PostgreSQL cluster");
            log.info("[order-service] Committed ACID transaction for OrderID: {} into PostgreSQL DB", orderId);
            Thread.sleep(50);
            Span.current().setStatus(StatusCode.OK, "DatabaseService: Committed ACID transaction into PostgreSQL DB");
            return true;
        } catch (InterruptedException e) {
            return false;
        } finally {
            span.end();
        }
    }

    public List<Order> getAllOrders() {
        return new ArrayList<>(orderRepository.values());
    }

    public Optional<Order> getOrderById(String orderId) {
        return Optional.ofNullable(orderRepository.get(orderId));
    }
}
