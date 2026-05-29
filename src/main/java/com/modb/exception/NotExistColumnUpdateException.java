package com.modb.exception;

public class NotExistColumnUpdateException extends RuntimeException {
    public NotExistColumnUpdateException(String message) {
        super(message);
    }
}
