package com.example.controller.dto.search;

import java.math.BigDecimal;

public record SearchResultDTO(
        Long productId,
        String productName,
        String productImage,
        BigDecimal price
) {
}
