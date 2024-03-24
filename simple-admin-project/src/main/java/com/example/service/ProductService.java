package com.example.service;

import com.example.domain.product.ProductDetailView;
import com.example.exception.ProductNotFoundException;
import com.example.repository.ProductRepository;
import com.example.service.dto.ProductDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional
@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }


    public List<ProductDTO> findAll() {
        List<ProductDetailView> allProductDetailViews = productRepository.findAllProductDetailView();
        return allProductDetailViews.stream()
                .map(pv -> ProductDTO.builder()
                        .productId(pv.productId())
                        .productName(pv.productName())
                        .imageUrl(pv.imageUrl())
                        .stockQuantity(pv.stockQuantity())
                        .price(pv.price())
                        .vendorId(pv.vendorId())
                        .vendorName(pv.vendorName())
                        .createdAt(pv.createdAt())
                        .createdBy(pv.createdBy())
                        .build()
                ).toList();
    }

    public ProductDetailView getProductDetail(Long productId) {
        return productRepository.getProductDetail(productId).orElseThrow(
                () -> new ProductNotFoundException("Not found product info")
        );
    }
}
