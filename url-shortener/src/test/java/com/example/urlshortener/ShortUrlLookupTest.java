package com.example.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 실제 Redis 와 mock 리포지토리로 L1 → L2 → DB 조회 순서와 DB 호출 횟수를 검증한다. */
class ShortUrlLookupTest extends IntegrationTestSupport {

    private static final String URL = "https://example.com/lookup";

    @Autowired
    private StringRedisTemplate redis;

    private ShortUrlRepository repo;
    private RedisUrlCache l2;
    private String key;

    @BeforeEach
    void setUp() {
        repo = mock(ShortUrlRepository.class);
        l2 = new RedisUrlCache(redis, Duration.ofDays(1), Duration.ofSeconds(60));
        // 테스트 간 캐시 초기화가 아직 없으므로 키를 테스트마다 고유하게 쓴다.
        key = "lk" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        when(repo.findByShortKey(key)).thenReturn(Optional.empty());
    }

    private LocalUrlCache newL1() {
        return new LocalUrlCache(Duration.ofMinutes(5), 1000);
    }

    private ShortUrlLookup newServer(LocalUrlCache l1) {
        return new ShortUrlLookup(l1, l2, repo);
    }

    @Test
    void dbHitFillsBothLevels() {
        when(repo.findByShortKey(key)).thenReturn(Optional.of(ShortUrl.create(URL, key)));
        ShortUrlLookup serverA = newServer(newL1());

        assertThat(serverA.findOriginalUrl(key)).contains(URL);
        assertThat(serverA.findOriginalUrl(key)).contains(URL);
        verify(repo, times(1)).findByShortKey(key);

        ShortUrlLookup serverB = newServer(newL1());
        assertThat(serverB.findOriginalUrl(key)).contains(URL);
        verify(repo, times(1)).findByShortKey(key);
    }

    @Test
    void l2HitFillsL1() {
        l2.putFound(key, URL);
        LocalUrlCache l1 = newL1();
        ShortUrlLookup server = newServer(l1);

        assertThat(server.findOriginalUrl(key)).contains(URL);
        verifyNoInteractions(repo);
        assertThat(l1.get(key)).contains(URL);
    }

    @Test
    void createdKeyServedWithoutDb() {
        newServer(newL1()).recordCreated(key, URL);

        assertThat(newServer(newL1()).findOriginalUrl(key)).contains(URL);
        verifyNoInteractions(repo);
    }

    @Test
    void missingKeyHitsDbOnce() {
        LocalUrlCache l1 = newL1();
        ShortUrlLookup server = newServer(l1);

        assertThat(server.findOriginalUrl(key)).isEmpty();
        assertThat(server.findOriginalUrl(key)).isEmpty();

        verify(repo, times(1)).findByShortKey(key);
        assertThat(l1.get(key)).isEmpty();
        assertThat(l2.get(key)).isInstanceOf(RedisCacheResult.NotFound.class);
    }

    @Test
    void negativeIsNotStaleAcrossServers() {
        ShortUrlLookup serverA = newServer(newL1());
        ShortUrlLookup serverB = newServer(newL1());

        assertThat(serverA.findOriginalUrl(key)).isEmpty();
        serverB.recordCreated(key, URL);

        assertThat(serverA.findOriginalUrl(key)).contains(URL);
        verify(repo, times(1)).findByShortKey(key);
    }
}
