package com.example.controller;

import com.example.dto.FinishOrderDto;
import com.example.dto.ProductOrderDetailDto;
import com.example.dto.StartOrderDto;
import com.example.dto.StartOrderResponseDto;
import com.example.entity.ProductOrder;
import com.example.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/order/start-order")
    public StartOrderResponseDto startOrder(@RequestBody StartOrderDto dto) {
        return orderService.startOrder(dto.userId(), dto.productId(), dto.count());
    }

    @PostMapping("/order/finish-order")
    public ProductOrder finishOrder(@RequestBody FinishOrderDto dto) {
        return orderService.finishOrder(dto.orderId(), dto.paymentMethodId(), dto.addressId());
    }

    @GetMapping("/order/users/{userId}/orders")
    public List<ProductOrder> getUserOrders(@PathVariable Long userId) {
        return orderService.getUserOrders(userId);
    }

    @GetMapping("/order/orders/{orderId}")
    public ProductOrderDetailDto getOrder(@PathVariable Long orderId) {
        return orderService.getOrderDetail(orderId);
    }
}
