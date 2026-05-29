package com.modb.exception;

public class InvalidCheckSumException extends RuntimeException {
    public InvalidCheckSumException(String message) {
        super(message);
    }
}
