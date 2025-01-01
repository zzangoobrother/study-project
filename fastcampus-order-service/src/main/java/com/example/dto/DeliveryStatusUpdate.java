package com.example.dto;

import com.example.enums.OrderStatus;

public record DeliveryStatusUpdate(
        Long orderId,
        Long paymentId,
        String deliveryStatus
) {
}
