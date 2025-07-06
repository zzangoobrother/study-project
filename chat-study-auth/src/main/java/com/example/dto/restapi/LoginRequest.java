package com.example.dto.restapi;

public record LoginRequest(
        String username,
        String password
) {
}
