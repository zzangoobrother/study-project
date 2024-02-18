package com.example.fastcampusredis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    private final RedisTemplate redisTemplate;

    public HelloController(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/hello")
    public String hello() {
        return "hello world!";
    }

    @GetMapping("/setFruit")
    public String setFruit(@RequestParam String name) {
        ValueOperations<String, String> valueOperations = redisTemplate.opsForValue();
        valueOperations.set("fruit", name);

        return "saved";
    }

    @GetMapping("/getFruit")
    public String getFruit() {
        ValueOperations<String, String> valueOperations = redisTemplate.opsForValue();
        String fruit = valueOperations.get("fruit");

        return fruit;
    }
}
