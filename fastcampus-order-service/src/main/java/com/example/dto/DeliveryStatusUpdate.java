package com.example.dto;

public record DeliveryStatusUpdate(
        Long orderId,
        Long deliveryId,
        String deliveryStatus
) {
}
