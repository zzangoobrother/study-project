package com.example.urlshortener;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 기반 L2 캐시. "있음"(url)과 "없음"(마커)을 서로 다른 TTL 로 저장한다.
 *
 * Redis 오류는 여기서 삼킨다. Redis 가 죽어도 리다이렉트와 생성은 DB 로 계속 동작해야 하기 때문이다.
 * get 은 Miss 로, put 은 무시로 처리하고 경고 로그만 남긴다.
 * 다만 장애가 길어지면 요청 하나가 (명령 타임아웃 × L2 호출 횟수(get + put))만큼 기다릴 수 있다.
 * 응답 없는 Redis 에 대한 get 한 번이 약 440ms 걸리는 것을 측정했다.
 * 그래서 실무에서는 서킷 브레이커로 Redis 호출 자체를 건너뛰어야 한다.
 */
class RedisUrlCache {

    static final String KEY_PREFIX = "short:";
    static final String NOT_FOUND_MARKER = "!none";

    private static final Logger log = LoggerFactory.getLogger(RedisUrlCache.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final Duration negativeTtl;

    RedisUrlCache(StringRedisTemplate redis, Duration ttl, Duration negativeTtl) {
        this.redis = redis;
        this.ttl = ttl;
        this.negativeTtl = negativeTtl;
    }

    RedisCacheResult get(String key) {
        try {
            String value = redis.opsForValue().get(KEY_PREFIX + key);
            if (value == null) {
                return new RedisCacheResult.Miss();
            }
            if (NOT_FOUND_MARKER.equals(value)) {
                return new RedisCacheResult.NotFound();
            }
            return new RedisCacheResult.Found(value);
        } catch (DataAccessException e) {
            log.warn("Redis 조회 실패, 미스로 처리한다. key={}", key, e);
            return new RedisCacheResult.Miss();
        }
    }

    void putFound(String key, String url) {
        put(key, url, ttl);
    }

    void putNotFound(String key) {
        put(key, NOT_FOUND_MARKER, negativeTtl);
    }

    private void put(String key, String value, Duration expire) {
        try {
            redis.opsForValue().set(KEY_PREFIX + key, value, expire);
        } catch (DataAccessException e) {
            log.warn("Redis 저장 실패, 무시한다. key={}", key, e);
        }
    }
}
