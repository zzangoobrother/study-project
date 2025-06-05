package com.example.restapi;

public record LoginRequest(
        String username,
        String password
) {
}
