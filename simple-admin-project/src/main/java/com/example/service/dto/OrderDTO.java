package com.example.service.dto;

import com.example.enums.OrderStatus;
import com.example.enums.PayType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderDTO(
        Long orderId,
        BigDecimal amount,
        Long customerId,
        String customerName,
        PayType payType,
        OrderStatus orderStatus,
        OffsetDateTime orderDate
) {

    @Builder
    public OrderDTO {}
}
