package com.example.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OrderController {

    @GetMapping({"/orders", "/orders/"})
    public String index() {
        return "/orders/orders";
    }

    @GetMapping("/orders/order-detail")
    public String detail() {
        return "/orders/order-detail";
    }
}
