package com.example.exception;

public class NotFoundAdminUserException extends RuntimeException {

    public NotFoundAdminUserException(String message) {
        super(message);
    }
}
