package com.example.dto;

public record UserUpdatePasswordRequest(
        String beforePassword,
        String afterPassword
) {
}
