package com.example.fastcampustestapi.controller;

import com.example.fastcampustestapi.domain.Order;
import com.example.fastcampustestapi.service.OrderQueryService;
import com.example.fastcampustestapi.service.dto.OrderDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
public class OrderController {

    private final OrderQueryService orderQueryService;

    public OrderController(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    @GetMapping({"/orders/", "/orders"})
    public List<OrderDTO> index(@PageableDefault(size=20, sort="orderId",direction = Sort.Direction.ASC) Pageable pageable) {
        log.info(">>> Request orders");
        Page<Order> orders = orderQueryService.findAll(pageable);
        return orders.stream().map(order -> OrderDTO.of(order.getOrderId(), order.getAmount(), order.getCustomerId(), order.getCreatedAt())).toList();
    }
}
