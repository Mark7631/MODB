package com.modb.exception;

public class TableDoesntExistsException extends RuntimeException {
    public TableDoesntExistsException(String message) {
        super(message);
    }
}
