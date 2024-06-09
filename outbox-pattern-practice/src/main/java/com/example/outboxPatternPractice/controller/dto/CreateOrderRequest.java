package com.example.outboxPatternPractice.controller.dto;

public record CreateOrderRequest(
        Long productId,
        int quantity
) {
}
