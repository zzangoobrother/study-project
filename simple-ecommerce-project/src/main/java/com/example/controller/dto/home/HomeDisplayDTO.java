package com.example.controller.dto.home;

import java.util.List;

public record HomeDisplayDTO(
        String imageBannerSrc,
        List<List<RecommendProductDTO>> recommendProductDTOs
) {
}
