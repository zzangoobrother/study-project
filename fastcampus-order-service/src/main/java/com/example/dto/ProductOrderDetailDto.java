package com.example.dto;

import com.example.enums.OrderStatus;

public record ProductOrderDetailDto(
        Long id,
        Long userId,
        Long productId,
        Long paymentId,
        Long deliveryId,
        OrderStatus orderStatus,
        String paymentStatus,
        String deliveryStatus
) {
}
