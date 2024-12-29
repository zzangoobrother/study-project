package com.example.dto;

import java.util.List;

public record RegisterProductDto(
        Long sellerId,
        String name,
        String description,
        Long price,
        Long stockCount,
        List<String> tags
) {
}
