package com.example.amzstore.exception;

public class InventoryOutOfStockException extends RuntimeException {
    public InventoryOutOfStockException(String message) {
        super(message);
    }
}
