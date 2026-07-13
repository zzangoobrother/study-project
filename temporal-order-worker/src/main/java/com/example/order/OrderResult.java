package com.example.order;

// 주문 처리 결과
public record OrderResult(String orderId, String paymentId, String reservationId, String shipmentId, String status) {
}
