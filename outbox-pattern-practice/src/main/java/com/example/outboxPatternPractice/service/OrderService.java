package com.example.outboxPatternPractice.service;

import com.example.outboxPatternPractice.entity.EventOutbox;
import com.example.outboxPatternPractice.entity.Order;
import com.example.outboxPatternPractice.entity.OrderDetail;
import com.example.outboxPatternPractice.repository.EventOutboxRepository;
import com.example.outboxPatternPractice.repository.OrderDetailRepository;
import com.example.outboxPatternPractice.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final EventOutboxRepository eventOutboxRepository;

    public OrderService(OrderRepository orderRepository, OrderDetailRepository orderDetailRepository, EventOutboxRepository eventOutboxRepository) {
        this.orderRepository = orderRepository;
        this.orderDetailRepository = orderDetailRepository;
        this.eventOutboxRepository = eventOutboxRepository;
    }

    @Transactional
    public void create(Long productId, Long userId, int quantity) {
        LocalDateTime now = LocalDateTime.now();
        UUID uuid = UUID.randomUUID();

        orderRepository.save(new Order(uuid, userId, now));
        orderDetailRepository.save(new OrderDetail(productId, quantity, now));
        eventOutboxRepository.save(new EventOutbox(uuid));
    }
}
