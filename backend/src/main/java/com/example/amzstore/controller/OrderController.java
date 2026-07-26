package com.example.amzstore.controller;

import com.example.amzstore.dto.ApiResponse;
import com.example.amzstore.dto.CheckoutRequest;
import com.example.amzstore.model.Order;
import com.example.amzstore.service.OrderService;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final Tracer tracer;

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<Order>> checkout(@RequestBody CheckoutRequest request) {
        Order order = orderService.processCheckout(request);

        String traceId = getTraceId();
        String spanId = getSpanId();

        if ("FAILED".equals(order.getStatus())) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(ApiResponse.<Order>builder()
                    .success(false)
                    .message("Order checkout failed: " + order.getFailureReason())
                    .data(order)
                    .traceId(traceId)
                    .spanId(spanId)
                    .build());
        }

        return ResponseEntity.ok(ApiResponse.<Order>builder()
                .success(true)
                .message("Order placed successfully!")
                .data(order)
                .traceId(traceId)
                .spanId(spanId)
                .build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Order>>> getAllOrders() {
        List<Order> orders = orderService.getAllOrders();
        return ResponseEntity.ok(ApiResponse.<List<Order>>builder()
                .success(true)
                .message("Orders retrieved")
                .data(orders)
                .traceId(getTraceId())
                .spanId(getSpanId())
                .build());
    }

    private String getTraceId() {
        return tracer.currentSpan() != null ? tracer.currentSpan().context().traceId() : "none";
    }

    private String getSpanId() {
        return tracer.currentSpan() != null ? tracer.currentSpan().context().spanId() : "none";
    }
}
