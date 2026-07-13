package com.example.payment;

// 결제 취소(보상) 요청 DTO
public record PaymentCancelRequest(String paymentId, String orderId) {
}
