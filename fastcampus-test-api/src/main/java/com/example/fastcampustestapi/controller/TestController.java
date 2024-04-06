package com.example.fastcampustestapi.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@Slf4j
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

    @GetMapping("/get")
    public String get(HttpServletRequest request) {
        String role = request.getHeader("role");
        log.info(role);

        return "get";
    }
}
