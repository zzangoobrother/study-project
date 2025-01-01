package com.example.dto;

public record DeliveryRequest(
        Long orderId,
        String productName,
        Long count,
        String address
) {
}
