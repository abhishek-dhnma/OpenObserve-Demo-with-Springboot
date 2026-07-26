package com.example.amzstore.controller;

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
@RequestMapping("/api/microservices")
public class MicroservicesPipelineController {

    @PostMapping("/auth/validate")
    public ResponseEntity<?> validateAuthSession(@RequestParam(defaultValue = "customer@example.com") String email) {
        log.info("[AuthService] Validating OAuth2 session token for customer: {}", email);
        try {
            Thread.sleep(45);
        } catch (InterruptedException ignored) {}
        Span.current().setStatus(StatusCode.OK, "AuthService: Session token validated");
        return ResponseEntity.ok(Map.of("status", "VALIDATED", "email", email));
    }

    @PostMapping("/inventory/reserve")
    public ResponseEntity<?> reserveInventoryStock(@RequestParam(defaultValue = "false") boolean simulateFailure, @RequestParam(defaultValue = "ORD-000") String orderId) {
        log.info("[InventoryService] Acquiring DB row locks for OrderID: {}", orderId);
        try {
            Thread.sleep(110);
        } catch (InterruptedException ignored) {}

        if (simulateFailure) {
            String errorMsg = "InventoryService Exception: Stock allocation failed for OrderID " + orderId + ". DB Row lock timeout.";
            log.error("[InventoryService] Stock allocation failed for OrderID: {}! Database lock timeout / Out of Stock.", orderId);
            Span.current().setStatus(StatusCode.ERROR, errorMsg);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", errorMsg, "code", "INVENTORY_ALLOCATION_FAILED_500"));
        }

        Span.current().setStatus(StatusCode.OK, "InventoryService: Reserved product stock in database");
        return ResponseEntity.ok(Map.of("status", "RESERVED", "orderId", orderId));
    }

    @PostMapping("/payment/authorize")
    public ResponseEntity<?> authorizePayment(@RequestParam(defaultValue = "false") boolean simulateFailure, @RequestParam(defaultValue = "100.00") String amount, @RequestParam(defaultValue = "ORD-000") String orderId) {
        log.info("[PaymentGatewayService] Contacting Stripe payment processor for OrderID: {}, Amount: ${}", orderId, amount);
        try {
            Thread.sleep(210);
        } catch (InterruptedException ignored) {}

        if (simulateFailure) {
            String errorMsg = "PaymentGateway Authorization Failed: Card declined by issuing bank (Code 402)";
            log.error("[PaymentGatewayService] Payment authorization REJECTED for OrderID: {}! Insufficient funds or invalid CVV.", orderId);
            Span.current().setStatus(StatusCode.ERROR, errorMsg);
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(Map.of("error", errorMsg, "code", "PAYMENT_DECLINED_402"));
        }

        String txnRef = "TXN-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        log.info("[PaymentGatewayService] Payment AUTHORIZED for OrderID: {}. Ref: {}", orderId, txnRef);
        Span.current().setStatus(StatusCode.OK, "PaymentGatewayService: Payment authorized via Stripe gateway");
        return ResponseEntity.ok(Map.of("status", "AUTHORIZED", "txnRef", txnRef));
    }

    @PostMapping("/fulfillment/ship")
    public ResponseEntity<?> createShippingLabel(@RequestParam(defaultValue = "false") boolean simulateFailure, @RequestParam(defaultValue = "ORD-000") String orderId) {
        log.info("[FulfillmentService] Contacting FedEx API for OrderID: {}", orderId);
        try {
            Thread.sleep(140);
        } catch (InterruptedException ignored) {}

        if (simulateFailure) {
            String errorMsg = "FulfillmentService Exception: Carrier API connection timeout (FedEx Service Unavailable 503)";
            log.error("[FulfillmentService] Failed to create shipping label for OrderID: {}! Carrier API timed out.", orderId);
            Span.current().setStatus(StatusCode.ERROR, errorMsg);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", errorMsg, "code", "CARRIER_API_TIMEOUT_503"));
        }

        String trackingId = "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[FulfillmentService] Created shipping label for OrderID: {}. TrackingID: {}", orderId, trackingId);
        Span.current().setStatus(StatusCode.OK, "FulfillmentService: Generated FedEx shipping label and tracking ID: " + trackingId);
        return ResponseEntity.ok(Map.of("status", "SHIPPED", "trackingId", trackingId));
    }
}
