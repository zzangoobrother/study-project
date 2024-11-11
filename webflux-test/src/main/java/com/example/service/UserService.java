package com.example.service;

import com.example.repository.User;
import com.example.repository.UserR2dbcRepository;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class UserService {

//    private final UserRepository userRepository;
    private final UserR2dbcRepository userR2dbcRepository;
    private final ReactiveRedisTemplate<String, User> redisTemplate;

    public UserService(UserR2dbcRepository userR2dbcRepository, ReactiveRedisTemplate<String, User> redisTemplate) {
        this.userR2dbcRepository = userR2dbcRepository;
        this.redisTemplate = redisTemplate;
    }

    public Mono<User> create(String name, String email) {
        return userR2dbcRepository.save(User.builder().name(name).email(email).build());
    }

    public Flux<User> findAll() {
        return userR2dbcRepository.findAll();
    }

    public Mono<User> findById(Long id) {
        return redisTemplate.opsForValue()
                .get(getUserCacheKey(id))
                .switchIfEmpty(
                        userR2dbcRepository.findById(id)
                        .flatMap(u -> redisTemplate.opsForValue().set(getUserCacheKey(id), u, Duration.ofSeconds(30))
                                .then(Mono.just(u))
                        )

                );
    }

    private String getUserCacheKey(Long id) {
        return "users:%d".formatted(id);
    }

    public Mono<Void> deleteById(Long id) {
        return userR2dbcRepository.deleteById(id)
                .then(redisTemplate.unlink(getUserCacheKey(id)))
                .then(Mono.empty());
    }

    public Mono<Void> deleteByName(String name) {
        return userR2dbcRepository.deleteByName(name);
    }

    public Mono<User> update(Long id, String name, String email) {
        return userR2dbcRepository.findById(id)
                .flatMap(u -> {
                    u.setName(name);
                    u.setEmail(email);
                    return userR2dbcRepository.save(u);
                })
                .flatMap(u -> redisTemplate.unlink(getUserCacheKey(id)).then(Mono.just(u)));
    }
}
