package com.example.amzstore.service;

import com.example.amzstore.dto.CheckoutRequest;
import com.example.amzstore.model.CartItem;
import com.example.amzstore.model.Order;
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
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, Order> orderRepository = new ConcurrentHashMap<>();

    private final String inventoryServiceUrl = "http://localhost:8081/api/inventory";
    private final String paymentServiceUrl = "http://localhost:8082/api/payment";
    private final String fulfillmentServiceUrl = "http://localhost:8083/api/fulfillment";

    public Order processCheckout(CheckoutRequest request) {
        String mainTraceId = tracer.currentSpan() != null ? tracer.currentSpan().context().traceId() : UUID.randomUUID().toString();
        String orderId = "ORD-" + System.currentTimeMillis();
        String customerEmail = request.getCustomerEmail() != null ? request.getCustomerEmail() : "customer@example.com";
        String failureType = request.getPaymentMethod() != null && request.isSimulateFailure() ? request.getPaymentMethod() : "NONE";

        log.info("[order-service] Initiating order checkout pipeline [OrderID: {}, Customer: {}, FailureType: {}]",
                orderId, customerEmail, failureType);

        // 1. AuthService (Internal Session Validation)
        validateCustomerSession(customerEmail, orderId);

        // 2. CartService (Cart Calculation)
        BigDecimal subtotal = fetchCartDetails(request.getItems(), orderId);

        // 3. HTTP Call to standalone inventory-service (Port 8081)
        boolean inventorySuccess = reserveInventoryStock(request.getItems(), orderId, "INVENTORY".equalsIgnoreCase(failureType));
        if (!inventorySuccess) {
            return createFailedOrder(orderId, customerEmail, request.getItems(), subtotal, mainTraceId,
                    "InventoryService Error: Stock allocation failed / Database lock timeout (500)");
        }

        // 4. PricingService
        BigDecimal finalAmount = calculateTaxesAndDiscounts(subtotal, orderId);

        // 5. FraudDetectionService
        evaluateFraudRiskScore(customerEmail, finalAmount, orderId);

        // 6. HTTP Call to standalone payment-service (Port 8082)
        boolean paymentSuccess = authorizePayment(orderId, finalAmount, "PAYMENT".equalsIgnoreCase(failureType) || request.isSimulateFailure());
        if (!paymentSuccess) {
            return createFailedOrder(orderId, customerEmail, request.getItems(), finalAmount, mainTraceId,
                    "PaymentGateway Error: Card authorization declined by issuing bank (Code 402)");
        }

        // 7. HTTP Call to standalone fulfillment-service (Port 8083)
        String trackingId = createShippingLabel(orderId, customerEmail, "SHIPPING".equalsIgnoreCase(failureType));
        if (trackingId == null) {
            return createFailedOrder(orderId, customerEmail, request.getItems(), finalAmount, mainTraceId,
                    "FulfillmentService Error: Carrier API address validation timeout (FedEx 503)");
        }

        // 8. NotificationService
        sendOrderConfirmationEmail(customerEmail, orderId, finalAmount);

        // 9. Database Persistence
        commitOrderTransaction(orderId, finalAmount, request.getItems());

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
            span.tag("microservice", "auth-service");
            span.tag("order.id", orderId);
            span.tag("customer.email", email);
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
            span.tag("microservice", "cart-service");
            span.tag("order.id", orderId);
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
        ScopedSpan span = tracer.startScopedSpan("Call :: inventory-service (Port 8081)");
        try {
            span.tag("microservice", "inventory-service");
            span.tag("order.id", orderId);

            String url = inventoryServiceUrl + "/reserve?simulateFailure=" + simulateFailure + "&orderId=" + orderId;
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
            String errorMsg = "HTTP POST to inventory-service (Port 8081) failed: " + e.getMessage();
            span.error(e);
            Span.current().setStatus(StatusCode.ERROR, errorMsg);
            span.tag("error.code", "INVENTORY_ALLOCATION_FAILED_500");
            return false;
        } finally {
            span.end();
        }
    }

    private BigDecimal calculateTaxesAndDiscounts(BigDecimal subtotal, String orderId) {
        ScopedSpan span = tracer.startScopedSpan("PricingService :: calculateTaxesAndDiscounts");
        try {
            span.tag("microservice", "pricing-service");
            span.tag("order.id", orderId);
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
            span.tag("microservice", "fraud-detection-service");
            span.tag("order.id", orderId);
            span.tag("risk.score", "0.02 (LOW)");
            log.info("[order-service] ML model evaluated transaction risk for {}: 0.02 (APPROVED)", email);
            Thread.sleep(80);
            Span.current().setStatus(StatusCode.OK, "FraudDetectionService: Transaction risk score 0.02 (Low / Approved)");
        } catch (InterruptedException ignored) {
        } finally {
            span.end();
        }
    }

    private boolean authorizePayment(String orderId, BigDecimal amount, boolean simulateFailure) {
        ScopedSpan span = tracer.startScopedSpan("Call :: payment-service (Port 8082)");
        try {
            span.tag("microservice", "payment-service");
            span.tag("order.id", orderId);
            span.tag("payment.amount", amount.toString());

            String url = paymentServiceUrl + "/authorize?simulateFailure=" + simulateFailure + "&amount=" + amount + "&orderId=" + orderId;
            restTemplate.postForEntity(url, null, Map.class);

            Span.current().setStatus(StatusCode.OK, "HTTP POST to payment-service (Port 8082) succeeded");
            return true;
        } catch (Exception e) {
            String errorMsg = "HTTP POST to payment-service (Port 8082) failed: Card declined (Code 402)";
            span.error(e);
            Span.current().setStatus(StatusCode.ERROR, errorMsg);
            span.tag("error.code", "PAYMENT_DECLINED_402");
            return false;
        } finally {
            span.end();
        }
    }

    private String createShippingLabel(String orderId, String email, boolean simulateFailure) {
        ScopedSpan span = tracer.startScopedSpan("Call :: fulfillment-service (Port 8083)");
        try {
            span.tag("microservice", "fulfillment-service");
            span.tag("order.id", orderId);

            String url = fulfillmentServiceUrl + "/ship?simulateFailure=" + simulateFailure + "&orderId=" + orderId;
            var response = restTemplate.postForEntity(url, null, Map.class);

            String trackingId = response.getBody() != null ? (String) response.getBody().get("trackingId") : "TRK-LOCAL";
            Span.current().setStatus(StatusCode.OK, "HTTP POST to fulfillment-service (Port 8083) succeeded: " + trackingId);
            return trackingId;
        } catch (Exception e) {
            String errorMsg = "HTTP POST to fulfillment-service (Port 8083) failed: Carrier API timeout (Code 503)";
            span.error(e);
            Span.current().setStatus(StatusCode.ERROR, errorMsg);
            span.tag("error.code", "CARRIER_API_TIMEOUT_503");
            return null;
        } finally {
            span.end();
        }
    }

    private void sendOrderConfirmationEmail(String email, String orderId, BigDecimal amount) {
        ScopedSpan span = tracer.startScopedSpan("NotificationService :: sendOrderConfirmationEmail");
        try {
            span.tag("microservice", "notification-service");
            span.tag("order.id", orderId);
            log.info("[order-service] Sent order confirmation email to: {}", email);
            Thread.sleep(90);
            Span.current().setStatus(StatusCode.OK, "NotificationService: Sent order confirmation email to " + email);
        } catch (InterruptedException ignored) {
        } finally {
            span.end();
        }
    }

    private void commitOrderTransaction(String orderId, BigDecimal amount, List<CartItem> items) {
        ScopedSpan span = tracer.startScopedSpan("DatabaseService :: commitOrderTransaction");
        try {
            span.tag("microservice", "database-service");
            span.tag("order.id", orderId);
            log.info("[order-service] Committed ACID transaction for OrderID: {} into PostgreSQL DB", orderId);
            Thread.sleep(50);
            Span.current().setStatus(StatusCode.OK, "DatabaseService: Committed ACID transaction into PostgreSQL DB");
        } catch (InterruptedException ignored) {
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
