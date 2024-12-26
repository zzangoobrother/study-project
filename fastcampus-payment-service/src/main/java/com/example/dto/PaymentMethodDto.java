package com.example.dto;

import com.example.enums.PaymentMethodType;

public record PaymentMethodDto(
        Long userId,
        PaymentMethodType paymentMethodType,
        String creditCardNumber
) {
}
