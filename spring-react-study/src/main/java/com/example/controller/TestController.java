package com.example.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/api/public/hello")
    public String publicHello() {
        return "Public Hello World";
    }

    @GetMapping("/api/private/hello")
    public String privateHello() {
        return "Private Hello World";
    }
}
