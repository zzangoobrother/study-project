package com.example.urlshortener;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class UrlCacheConfig {

    @Bean
    LocalUrlCache localUrlCache(
            @Value("${shortener.cache.local.ttl:5m}") Duration ttl,
            @Value("${shortener.cache.local.max-size:100000}") long maxSize) {
        return new LocalUrlCache(ttl, maxSize);
    }
}
