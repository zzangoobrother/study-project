package com.example.service.product;

import com.example.domain.product.Product;
import com.example.repository.product.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Optional<Product> findById(Long productId) {
        return productRepository.findById(productId);
    }
}
