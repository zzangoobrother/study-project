package com.example.dto;

import com.example.enums.PaymentStatus;

public record PaymentResult(
        Long orderId,
        Long paymentId,
        PaymentStatus paymentStatus
) {
}
