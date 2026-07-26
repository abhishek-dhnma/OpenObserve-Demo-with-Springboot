package com.example.amzstore.controller;

import com.example.amzstore.dto.ApiResponse;
import com.example.amzstore.dto.CheckoutRequest;
import com.example.amzstore.model.CartItem;
import com.example.amzstore.model.Product;

import com.example.amzstore.service.OrderService;
import com.example.amzstore.service.ProductService;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

@Slf4j
@RestController
@RequestMapping("/api/simulate")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SimulationController {

    private final OrderService orderService;
    private final ProductService productService;
    private final Tracer tracer;
    private final Random random = new Random();

    @PostMapping("/traffic")
    public ResponseEntity<ApiResponse<String>> simulateTraffic(@RequestParam(defaultValue = "5") int count) {
        log.info("Starting simulation of {} traffic requests for OpenObserve ingestion...", count);
        List<Product> products = productService.getAllProducts(null, null);

        int successfulCount = 0;
        int failedCount = 0;

        for (int i = 0; i < count; i++) {
            boolean simulateError = (i % 3 == 0); // Every 3rd request has simulated payment failure
            Product p = products.get(random.nextInt(products.size()));

            CheckoutRequest req = new CheckoutRequest();
            req.setCustomerEmail("simulated_user_" + i + "@example.com");
            req.setPaymentMethod(simulateError ? "INVALID_CARD" : "VISA");
            req.setSimulateFailure(simulateError);
            req.setItems(List.of(
                    CartItem.builder()
                            .productId(p.getId())
                            .productName(p.getName())
                            .quantity(1)
                            .unitPrice(p.getPrice())
                            .build()
            ));

            var order = orderService.processCheckout(req);
            if ("PAID".equals(order.getStatus())) {
                successfulCount++;
            } else {
                failedCount++;
            }
        }

        String message = String.format("Simulation complete! Generated %d total requests (%d Successful, %d Failed/Warned)",
                count, successfulCount, failedCount);

        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message(message)
                .data(message)
                .traceId(getTraceId())
                .spanId(getSpanId())
                .build());
    }

    @PostMapping("/error")
    public ResponseEntity<ApiResponse<String>> triggerErrorScenario() {
        log.error("CRITICAL ERROR SIMULATION: Uncaught DatabaseConnectionTimeoutException triggered for OpenObserve alert testing!");
        try {
            // Throw a simulated runtime exception to verify error trace spans & stack trace logging in OpenObserve
            throw new IllegalStateException("Simulated System Failure: Database Connection Pool Exhausted (Pool size: 0/10 active connections)");
        } catch (Exception e) {
            log.error("Exception captured in controller: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(ApiResponse.<String>builder()
                    .success(false)
                    .message("Simulated System Error: " + e.getMessage())
                    .traceId(getTraceId())
                    .spanId(getSpanId())
                    .build());
        }
    }

    private String getTraceId() {
        return tracer.currentSpan() != null ? tracer.currentSpan().context().traceId() : "none";
    }

    private String getSpanId() {
        return tracer.currentSpan() != null ? tracer.currentSpan().context().spanId() : "none";
    }
}
