package com.example.order;

// 결제 Activity 결과. downstream 응답(paymentId, status)과 필드가 일치해 역직렬화에 그대로 쓰인다.
public record PaymentResult(String paymentId, String status) {
}
