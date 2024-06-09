package com.example.outboxPatternPractice.repository;

import com.example.outboxPatternPractice.entity.OrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {
}
