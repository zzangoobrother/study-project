package com.example.dto;

public record StartOrderDto(
        Long userId,
        Long productId,
        Long count
) {
}
