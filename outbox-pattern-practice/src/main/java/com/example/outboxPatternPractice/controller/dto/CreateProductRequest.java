package com.example.outboxPatternPractice.controller.dto;

public record CreateProductRequest(
        String name,
        int quantity
) {
}
