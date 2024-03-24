package com.example.service.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ProductDTO(
        Long productId,
        String productName,
        String imageUrl,
        int stockQuantity,
        BigDecimal price,
        Long vendorId,
        String vendorName,
        OffsetDateTime createdAt,
        String createdBy
) {

    @Builder
    public ProductDTO {}
}
