package com.example.payment.controller;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    @PostMapping("/authorize")
    public ResponseEntity<?> authorizePayment(
            @RequestParam(defaultValue = "false") boolean simulateFailure,
            @RequestParam(defaultValue = "100.00") String amount,
            @RequestParam(defaultValue = "ORD-000") String orderId) {

        log.info("[payment-service] Contacting payment processor for OrderID: {}, Amount: ${}", orderId, amount);
        try {
            Thread.sleep(210);
        } catch (InterruptedException ignored) {}

        if (simulateFailure) {
            String errorMsg = "PaymentGateway Authorization Failed: Card declined by issuing bank (Code 402)";
            log.error("[payment-service] Payment authorization REJECTED for OrderID: {}! Insufficient funds or invalid CVV.", orderId);
            Span.current().setStatus(StatusCode.ERROR, errorMsg);
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(Map.of("error", errorMsg, "code", "PAYMENT_DECLINED_402"));
        }

        String txnRef = "TXN-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        log.info("[payment-service] Payment AUTHORIZED for OrderID: {}. Ref: {}", orderId, txnRef);
        Span.current().setStatus(StatusCode.OK, "payment-service: Payment authorized via Stripe gateway");
        return ResponseEntity.ok(Map.of("status", "AUTHORIZED", "txnRef", txnRef));
    }
}
