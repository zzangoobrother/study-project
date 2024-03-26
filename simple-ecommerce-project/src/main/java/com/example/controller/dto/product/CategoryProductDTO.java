package com.example.controller.dto.product;

import java.math.BigDecimal;

public record CategoryProductDTO(
        Long categoryProductId,
        Long categoryId,
        Long productId,
        String productName,
        String productImage,
        BigDecimal price
) {
}
