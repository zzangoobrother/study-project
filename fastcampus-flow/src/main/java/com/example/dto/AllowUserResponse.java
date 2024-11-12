package com.example.dto;

public record AllowUserResponse(
        Long requestCount,
        Long allowedCount
) {
}
