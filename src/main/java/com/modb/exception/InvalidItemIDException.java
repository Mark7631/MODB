package com.modb.exception;

public class InvalidItemIDException extends RuntimeException {
    public InvalidItemIDException(String message) {
        super(message);
    }
}
