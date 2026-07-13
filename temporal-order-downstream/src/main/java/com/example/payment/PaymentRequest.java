package com.example.payment;

// 결제 요청 DTO
public record PaymentRequest(String orderId, long amount) {
}
