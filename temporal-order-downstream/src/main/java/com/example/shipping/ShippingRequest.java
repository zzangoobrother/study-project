package com.example.shipping;

// 배송 요청 DTO
public record ShippingRequest(String orderId, String address) {
}
