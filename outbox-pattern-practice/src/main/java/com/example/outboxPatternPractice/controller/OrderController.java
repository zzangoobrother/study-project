package com.example.outboxPatternPractice.controller;

import com.example.outboxPatternPractice.controller.dto.CreateOrderRequest;
import com.example.outboxPatternPractice.service.OrderService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/api/orders")
    public void create(@RequestBody CreateOrderRequest request) {
        orderService.create(request.productId(), request.quantity());
    }
}
