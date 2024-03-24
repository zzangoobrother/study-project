package com.example.domain;

import com.example.enums.OrderStatus;
import com.example.enums.PayType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderDetailView(
        Long orderId,
        Long customerId,
        String customerName,
        BigDecimal amount,
        OrderStatus orderStatus,
        PayType payType,
        OffsetDateTime createdAt
) {
}
