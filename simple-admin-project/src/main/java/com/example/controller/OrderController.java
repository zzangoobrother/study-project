package com.example.controller;

import com.example.domain.OrderDetailView;
import com.example.service.OrderService;
import com.example.service.dto.OrderDTO;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping({"/orders", "/orders/"})
    public String index(Model model) {
        List<OrderDetailView> orderDetailViews = orderService.findAllOrderDetailView();
        model.addAttribute("orders", orderDetailViews);
        return "/orders/orders";
    }

    @GetMapping("/orders/order-detail")
    public String detail(@RequestParam Long orderId, Model model) {
        OrderDTO orderDTO = orderService.findById(orderId);
        model.addAttribute("order", orderDTO);
        return "/orders/order-detail";
    }
}
