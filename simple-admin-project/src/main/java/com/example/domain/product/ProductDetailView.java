package com.example.domain.product;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ProductDetailView(
        Long productId,
        String productName,
        String imageUrl,
        int stockQuantity,
        BigDecimal price,
        boolean isExposed,
        Long vendorId,
        String vendorName,
        OffsetDateTime createdAt,
        String createdBy
) {
}
