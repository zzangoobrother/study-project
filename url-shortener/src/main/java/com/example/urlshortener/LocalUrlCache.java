package com.example.urlshortener;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Optional;

/**
 * 서버 프로세스 안의 L1 캐시. "있음"(key -> url)만 저장한다.
 * "없음"을 L1 에 두면 다른 서버가 생성한 키가 L1 TTL 동안 404 가 되므로, "없음"은 L2(Redis)에만 둔다.
 */
class LocalUrlCache {

    private final Cache<String, String> cache;

    LocalUrlCache(Duration ttl, long maxSize) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl 은 양수여야 한다: " + ttl);
        }
        if (maxSize < 1) {
            throw new IllegalArgumentException("maxSize 는 1 이상이어야 한다: " + maxSize);
        }
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxSize)
                .build();
    }

    Optional<String> get(String key) {
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    void put(String key, String url) {
        cache.put(key, url);
    }

    void clear() {
        cache.invalidateAll();
    }
}
