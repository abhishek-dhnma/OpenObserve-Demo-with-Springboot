package com.example.amzstore.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    private String orderId;
    private String customerEmail;
    private List<CartItem> items;
    private BigDecimal totalAmount;
    private String status; // CREATED, PAID, FAILED, OUT_OF_STOCK
    private String traceId;
    private LocalDateTime createdAt;
    private String failureReason;
}
