package com.example.outboxPatternConsumerPractice.repository;

import com.example.outboxPatternConsumerPractice.entity.OrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {

    List<OrderDetail> findAllByOrderId(Long orderId);
}
