package com.example.dto;

public record FinishOrderDto(
        Long orderId,
        Long paymentMethodId,
        Long addressId
) {
}
