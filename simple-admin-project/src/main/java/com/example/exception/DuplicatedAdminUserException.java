package com.example.exception;

public class DuplicatedAdminUserException extends RuntimeException {

    public DuplicatedAdminUserException(String message) {
        super(message);
    }
}
