package com.example.fastcampustestapi.service.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderDTO(
        Long orderId,
        BigDecimal amount,
        Long customerId,
        OffsetDateTime orderDate
) {

    public static OrderDTO of(Long orderId, BigDecimal amount, Long customerId, OffsetDateTime orderDate) {
        return new OrderDTO(orderId, amount, customerId, orderDate);
    }
}
