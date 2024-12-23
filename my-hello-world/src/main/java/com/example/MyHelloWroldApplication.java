package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;

@Controller
@SpringBootApplication
public class MyHelloWroldApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyHelloWroldApplication.class, args);
    }
}
