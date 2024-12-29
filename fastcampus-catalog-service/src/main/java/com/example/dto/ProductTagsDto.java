package com.example.dto;

import java.util.List;

public record ProductTagsDto(
        Long productId,
        List<String> tags
) {
}
