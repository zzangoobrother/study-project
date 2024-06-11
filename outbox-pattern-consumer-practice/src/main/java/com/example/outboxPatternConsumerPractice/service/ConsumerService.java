package com.example.outboxPatternConsumerPractice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ConsumerService {

    private final OrderService orderService;

    public ConsumerService(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = "${rabbitmq.queue.name}")
    public void listener(String code) {
        log.info("rabbitmq start");
        orderService.order(code);
    }
}
