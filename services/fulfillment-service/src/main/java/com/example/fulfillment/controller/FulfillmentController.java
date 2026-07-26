package com.example.fulfillment.controller;

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
@RequestMapping("/api/fulfillment")
public class FulfillmentController {

    @PostMapping("/ship")
    public ResponseEntity<?> createShippingLabel(
            @RequestParam(defaultValue = "false") boolean simulateFailure,
            @RequestParam(defaultValue = "ORD-000") String orderId) {

        log.info("[fulfillment-service] Contacting FedEx API for OrderID: {}", orderId);
        try {
            Thread.sleep(140);
        } catch (InterruptedException ignored) {}

        if (simulateFailure) {
            RuntimeException ex = new RuntimeException("CarrierServiceUnavailableException: FedEx Carrier API connection timeout (FedEx Service Unavailable 503)");
            log.error("[fulfillment-service] Failed to create shipping label for OrderID: {}! Carrier API timed out.", orderId, ex);
            
            Span.current().recordException(ex);
            Span.current().setStatus(StatusCode.ERROR, ex.getMessage());
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", ex.getMessage(), "code", "CARRIER_API_TIMEOUT_503"));
        }

        String trackingId = "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[fulfillment-service] Created shipping label for OrderID: {}. TrackingID: {}", orderId, trackingId);
        Span.current().setStatus(StatusCode.OK, "fulfillment-service: Generated FedEx shipping label and tracking ID: " + trackingId);
        return ResponseEntity.ok(Map.of("status", "SHIPPED", "trackingId", trackingId));
    }
}
