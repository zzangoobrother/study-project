package com.example.dto;

public record ProcessPaymentDto(
        Long orderId,
        Long userId,
        Long amountKRW,
        Long paymentMethodId
) {
}
