package com.example.outboxPatternConsumerPractice.repository;

import com.example.outboxPatternConsumerPractice.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByCode(UUID code);
}
