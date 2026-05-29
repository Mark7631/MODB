package com.modb.exception;

public class NotInitedDBException extends RuntimeException {
    public NotInitedDBException(String message) {
        super(message);
    }
}
