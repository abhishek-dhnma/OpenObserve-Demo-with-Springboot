package com.example.amzstore.exception;

public class PaymentGatewayDeclinedException extends RuntimeException {
    public PaymentGatewayDeclinedException(String message) {
        super(message);
    }
}
