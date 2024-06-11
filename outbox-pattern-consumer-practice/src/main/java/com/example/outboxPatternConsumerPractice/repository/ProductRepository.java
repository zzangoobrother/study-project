package com.example.outboxPatternConsumerPractice.repository;

import com.example.outboxPatternConsumerPractice.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
