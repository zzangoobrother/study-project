package com.example.exception;

public class NotFoundCartItemException extends RuntimeException {

    public NotFoundCartItemException(String message) {
        super(message);
    }
}
