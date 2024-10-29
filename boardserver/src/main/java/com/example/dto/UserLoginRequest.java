package com.example.dto;

public record UserLoginRequest(
        String userId,
        String password
) {
}
