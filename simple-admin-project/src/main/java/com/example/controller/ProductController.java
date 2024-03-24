package com.example.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProductController {

    @GetMapping("/products")
    public String index() {
        return "/products/products";
    }

    @GetMapping("/products/product-detail")
    public String detail() {
        return "/products/product-detail";
    }
}
