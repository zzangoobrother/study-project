package com.example.controller.dto.home;

import java.math.BigDecimal;

public record RecommendProductDTO(
        Long productId,
        String productName,
        BigDecimal productPrice,
        String productImageUrl
) {
}
