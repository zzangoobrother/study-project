package com.example.dto;

import com.example.enums.DeliveryStatus;

public record DeliveryStatusUpdateDto(
        Long orderId,
        Long deliveryId,
        DeliveryStatus deliveryStatus
) {
}
