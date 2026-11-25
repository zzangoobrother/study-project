package com.example.urlshortener;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
class UrlCacheConfig {

    @Bean
    LocalUrlCache localUrlCache(
            @Value("${shortener.cache.local.ttl:5m}") Duration ttl,
            @Value("${shortener.cache.local.max-size:100000}") long maxSize) {
        return new LocalUrlCache(ttl, maxSize);
    }

    @Bean
    RedisUrlCache redisUrlCache(
            StringRedisTemplate redis,
            @Value("${shortener.cache.redis.ttl:1d}") Duration ttl,
            @Value("${shortener.cache.redis.negative-ttl:60s}") Duration negativeTtl) {
        return new RedisUrlCache(redis, ttl, negativeTtl);
    }
}
