package com.example.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/private")
@RestController
public class TestController {

    @GetMapping("/hello")
    public String privateHello() {
        return "인증된 사용자";
    }
}
