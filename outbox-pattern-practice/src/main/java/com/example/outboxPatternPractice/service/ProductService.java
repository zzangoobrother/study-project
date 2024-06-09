package com.example.outboxPatternPractice.service;

import com.example.outboxPatternPractice.entity.Product;
import com.example.outboxPatternPractice.repository.ProductRepository;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Long create(String name, int quantity) {
        return productRepository.save(new Product(name, quantity)).getId();
    }
}
