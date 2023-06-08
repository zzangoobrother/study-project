package com.example.concurrencystudy.repository;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisLockRepository {

    private final RedisTemplate<String, String> redisTemplete;

    public RedisLockRepository(RedisTemplate<String, String> redisTemplete) {
        this.redisTemplete = redisTemplete;
    }

    public Boolean lock(Long key) {
        return redisTemplete.opsForValue().setIfAbsent(generateKey(key), "lock", Duration.ofMillis(3_000));
    }

    public Boolean unLock(Long key) {
        return redisTemplete.delete(generateKey(key));
    }

    private String generateKey(Long key) {
        return key.toString();
    }
}
