package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@SpringBootApplication
public class WebfluxmockmvcapiApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebfluxmockmvcapiApplication.class, args);
    }

    @GetMapping("/posts/{id}")
    public Map<String, String> getPosts(@PathVariable("id") Long id) throws Exception {
        Thread.sleep(300);
        if (id > 10) {
            throw new Exception("Too long");
        }
        return Map.of("id", id.toString(), "content", "Posts content is %d".formatted(id));
    }
}
