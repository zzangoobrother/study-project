package com.example.outboxPatternPractice.repository;

import com.example.outboxPatternPractice.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
