package com.example.fastcampusredis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class FastcampusRedisApplication {

    public static void main(String[] args) {
        SpringApplication.run(FastcampusRedisApplication.class, args);
    }
}
