package com.example.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class SearchService {

    private final StringRedisTemplate stringRedisTemplate;

    public SearchService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void addTagCache(Long productId, List<String> tags) {
        tags.forEach(it -> stringRedisTemplate.opsForSet().add(it, productId.toString()));
    }

    public void removeTagCache(Long productId, List<String> tags) {
        tags.forEach(it -> stringRedisTemplate.opsForSet().remove(it, productId.toString()));
    }

    public List<Long> getProductIdsByTag(String tag) {
        Set<String> productIds = stringRedisTemplate.opsForSet().members(tag);

        if (productIds != null) {
            return productIds.stream()
                    .map(Long::parseLong)
                    .toList();
        }

        return Collections.emptyList();
    }
}
