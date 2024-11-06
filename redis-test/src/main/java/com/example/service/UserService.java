package com.example.service;

import com.example.domain.entity.User;
import com.example.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RedisTemplate<String, User> userRedisTemplate;
    private final RedisTemplate<String, Object> objectRedisTemplate;

    public User getUser(final Long id) {
        String key = "users:%d".formatted(id);

        Object cachedUser = objectRedisTemplate.opsForValue().get(key);
        if (cachedUser != null) {
            return (User) cachedUser;
        }

        User user = userRepository.findById(id).orElseThrow();
        objectRedisTemplate.opsForValue().set(key, user, Duration.ofSeconds(10));

        return user;
    }
}
