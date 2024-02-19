package com.example.fastcampusredis.service;

import com.example.fastcampusredis.dto.UserProfile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class UserService {

    public final ExternalApiService externalApiService;
    private final RedisTemplate redisTemplate;

    public UserService(ExternalApiService externalApiService, RedisTemplate redisTemplate) {
        this.externalApiService = externalApiService;
        this.redisTemplate = redisTemplate;
    }

    public UserProfile getUserProfile(String userId) {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();

        String cachedName = ops.get("nameKey:" + userId);
        String userName = null;
        if (cachedName != null) {
            userName = cachedName;
        } else {
            userName = externalApiService.getUserName(userId);
            ops.set("nameKey:" + userId, userName, 5, TimeUnit.SECONDS);
        }

        int userAge = externalApiService.getUserAge(userId);

        return new UserProfile(userName, userAge);
    }
}
