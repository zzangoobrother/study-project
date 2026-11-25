package com.example.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisUrlCacheTest extends IntegrationTestSupport {

    @Autowired
    StringRedisTemplate redis;

    RedisUrlCache cache;

    @BeforeEach
    void setUp() {
        cache = new RedisUrlCache(redis, Duration.ofDays(1), Duration.ofSeconds(60));
    }

    private String uniqueKey() {
        return "t" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void missWhenAbsent() {
        assertThat(cache.get(uniqueKey())).isInstanceOf(RedisCacheResult.Miss.class);
    }

    @Test
    void foundAfterPutFound() {
        String key = uniqueKey();
        cache.putFound(key, "https://example.com/a");

        assertThat(cache.get(key)).isEqualTo(new RedisCacheResult.Found("https://example.com/a"));
        Long ttl = redis.getExpire(RedisUrlCache.KEY_PREFIX + key);
        assertThat(ttl).isGreaterThan(0).isLessThanOrEqualTo(86400);
    }

    @Test
    void notFoundAfterPutNotFound() {
        String key = uniqueKey();
        cache.putNotFound(key);

        assertThat(cache.get(key)).isInstanceOf(RedisCacheResult.NotFound.class);
        Long ttl = redis.getExpire(RedisUrlCache.KEY_PREFIX + key);
        assertThat(ttl).isGreaterThan(0).isLessThanOrEqualTo(60);
        assertThat(redis.opsForValue().get(RedisUrlCache.KEY_PREFIX + key)).isEqualTo("!none");
    }

    @Test
    void putFoundOverwritesNotFound() {
        String key = uniqueKey();
        cache.putNotFound(key);
        cache.putFound(key, "https://example.com/b");

        assertThat(cache.get(key)).isEqualTo(new RedisCacheResult.Found("https://example.com/b"));
        assertThat(redis.getExpire(RedisUrlCache.KEY_PREFIX + key)).isGreaterThan(60);
    }
}
