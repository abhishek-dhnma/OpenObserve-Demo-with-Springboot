package com.example.amzstore.exception;

public class DatabaseConnectionTimeoutException extends RuntimeException {
    public DatabaseConnectionTimeoutException(String message) {
        super(message);
    }
}
