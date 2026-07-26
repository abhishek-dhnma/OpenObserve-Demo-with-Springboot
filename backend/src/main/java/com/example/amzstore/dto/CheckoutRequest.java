package com.example.amzstore.dto;

import com.example.amzstore.model.CartItem;
import lombok.Data;

import java.util.List;

@Data
public class CheckoutRequest {
    private String customerEmail;
    private List<CartItem> items;
    private String paymentMethod;
    private boolean simulateFailure;
}
