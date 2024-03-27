package com.example.controller.dto.product;

import com.example.domain.product.Product;
import com.example.enums.DeliveryType;
import com.example.enums.ProductStatus;
import lombok.Builder;

import java.math.BigDecimal;

public record ProductDTO(
        Long productId,
        String productName,
        BigDecimal price,
        Long vendorId,
        ProductStatus status,
        String imageUrl,
        String imageDetailUrl,
        DeliveryType deliveryType,
        String productDesc
) {

    @Builder
    public ProductDTO {}

    public static ProductDTO of(Product product) {
        return ProductDTO.builder()
                .productId(product.getId())
                .productName(product.getProductName())
                .price(product.getPrice())
                .vendorId(product.getVendorId())
                .status(product.getStatus())
                .imageUrl(product.getImageUrl())
                .imageDetailUrl(product.getImageDetailUrl())
                .productDesc(product.getProductDesc())
                .deliveryType(product.getDeliveryType())
                .build();
    }
}
