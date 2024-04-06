package com.example.fastcampustestapi.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello Get";
    }

    @GetMapping("/microwervice-hello")
    public String microServiceHello() {
        return "Micro Service Hello Get";
    }
}
