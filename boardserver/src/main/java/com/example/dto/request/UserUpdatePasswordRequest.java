package com.example.dto.request;

public record UserUpdatePasswordRequest(
        String beforePassword,
        String afterPassword
) {
}
