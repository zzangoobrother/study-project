package com.example.helloboot;

import org.springframework.web.bind.annotation.RequestParam;

import java.util.Objects;

public class HelloController {

    private final HelloService helloService;

    public HelloController(HelloService helloService) {
        this.helloService = helloService;
    }

    public String hello(@RequestParam(name = "name") String name) {
        return helloService.sayHello(Objects.requireNonNull(name));
    }
}
