package com.example.fastcampuseventdrivenorder;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class OrderController {
    private final RedisTemplate redisTemplate;

    public OrderController(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/order")
    public String order (@RequestParam String userId, @RequestParam String productId, @RequestParam String price) {
        Map<String, String> fieldMap = new HashMap<>();
        fieldMap.put("userId", userId);
        fieldMap.put("productId", productId);
        fieldMap.put("price", price);

        redisTemplate.opsForStream().add("order-event", fieldMap);

        System.out.println("Order created.");
        return "ok";
    }
}
