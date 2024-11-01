package com.example.dto.request;

public record UserLoginRequest(
        String userId,
        String password
) {
}
