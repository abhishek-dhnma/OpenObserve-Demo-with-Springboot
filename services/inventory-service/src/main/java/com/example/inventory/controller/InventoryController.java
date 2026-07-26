package com.example.inventory.controller;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    @PostMapping("/reserve")
    public ResponseEntity<?> reserveInventoryStock(@RequestParam(defaultValue = "false") boolean simulateFailure, @RequestParam(defaultValue = "ORD-000") String orderId) {
        log.info("[inventory-service] Acquiring DB locks to reserve stock for OrderID: {}", orderId);
        try {
            Thread.sleep(110);
        } catch (InterruptedException ignored) {}

        if (simulateFailure) {
            RuntimeException ex = new IllegalStateException("InventoryOutOfStockException: Stock allocation lock failed for OrderID " + orderId + ". DB Row lock timeout.");
            log.error("[inventory-service] Stock allocation failed for OrderID: {}! Database lock timeout / Out of Stock.", orderId, ex);
            
            Span.current().recordException(ex);
            Span.current().setStatus(StatusCode.ERROR, ex.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", ex.getMessage(), "code", "INVENTORY_ALLOCATION_FAILED_500"));
        }

        log.info("[inventory-service] Stock reserved successfully for OrderID: {}", orderId);
        Span.current().setStatus(StatusCode.OK, "inventory-service: Reserved product stock in database");
        return ResponseEntity.ok(Map.of("status", "RESERVED", "orderId", orderId));
    }
}
