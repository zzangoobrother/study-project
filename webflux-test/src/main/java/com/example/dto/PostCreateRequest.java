package com.example.dto;

public record PostCreateRequest(
        Long userId,
        String title,
        String content
) {
}
