package com.example.dto;

public record UserUpdateRequest(
        String name,
        String email
) {
}
