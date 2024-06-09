package com.example.outboxPatternPractice.controller.dto;

public record CreateOrderRequest(
        Long productId,
        Long userId,
        int quantity
) {
}
