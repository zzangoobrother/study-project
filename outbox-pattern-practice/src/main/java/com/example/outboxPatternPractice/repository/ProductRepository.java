package com.example.outboxPatternPractice.repository;

import com.example.outboxPatternPractice.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
