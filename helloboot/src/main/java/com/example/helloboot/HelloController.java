package com.example.helloboot;

import org.springframework.web.bind.annotation.RequestParam;

public class HelloController {

    public String hello(@RequestParam(name = "name") String name) {
        return "Hello " + name;
    }
}
