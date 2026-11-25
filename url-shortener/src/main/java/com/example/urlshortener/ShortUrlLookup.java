package com.example.urlshortener;

import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * L1(Caffeine) → L2(Redis) → DB 순서로 원본 URL 을 조회한다.
 *
 * <p>"없음"은 L2 에만 남긴다. L1 에 두면 다른 서버가 방금 생성한 키가 L1 TTL 동안 404 가 되기 때문이다.
 *
 * <p>남는 틈(알고 받아들인다). 두 경우 모두 상한은 negative TTL(60초)이다.
 * <ul>
 *   <li>짧은 경쟁: 서버 A 가 DB 에서 "없음"을 확인한 직후 서버 B 가 생성해 L2 에 "있음"을 쓰고,
 *       그 뒤에 A 가 "없음"을 쓰면 "있음"이 덮여 최대 60초 동안 404 가 날 수 있다.</li>
 *   <li>write-through 실패: {@link #recordCreated} 의 Redis 쓰기가 실패하면, 그 키에 앞서 남아 있던
 *       "없음"이 최대 60초 유지될 수 있다.</li>
 * </ul>
 */
@Component
class ShortUrlLookup {

    private final LocalUrlCache l1;
    private final RedisUrlCache l2;
    private final ShortUrlRepository repository;

    ShortUrlLookup(LocalUrlCache l1, RedisUrlCache l2, ShortUrlRepository repository) {
        this.l1 = l1;
        this.l2 = l2;
        this.repository = repository;
    }

    Optional<String> findOriginalUrl(String key) {
        Optional<String> local = l1.get(key);
        if (local.isPresent()) {
            return local;
        }

        RedisCacheResult cached = l2.get(key);
        if (cached instanceof RedisCacheResult.Found found) {
            l1.put(key, found.url());
            return Optional.of(found.url());
        }
        if (cached instanceof RedisCacheResult.NotFound) {
            return Optional.empty();
        }

        // Miss(Redis 오류 포함): DB 를 조회한다.
        Optional<String> fromDb = repository.findByShortKey(key).map(ShortUrl::getOriginalUrl);
        if (fromDb.isPresent()) {
            l1.put(key, fromDb.get());
            l2.putFound(key, fromDb.get());
        } else {
            l2.putNotFound(key);
        }
        return fromDb;
    }

    /** 생성 직후 L2 에 "있음"을 쓴다(남아 있던 "없음"도 덮어쓴다). L1 은 다른 서버에 도움이 안 되므로 쓰지 않는다. */
    void recordCreated(String key, String url) {
        l2.putFound(key, url);
    }
}
