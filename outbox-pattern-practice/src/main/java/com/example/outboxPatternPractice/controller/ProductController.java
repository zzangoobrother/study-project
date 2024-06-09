package com.example.outboxPatternPractice.controller;

import com.example.outboxPatternPractice.controller.dto.CreateProductRequest;
import com.example.outboxPatternPractice.service.ProductService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping("/api/products")
    public Long create(@RequestBody CreateProductRequest request) {
        return productService.create(request.name(), request.quantity());
    }
}
