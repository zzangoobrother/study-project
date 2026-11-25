package com.example.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LocalUrlCacheTest {

    @Test
    void returnsWhatWasPut() {
        LocalUrlCache cache = new LocalUrlCache(Duration.ofMinutes(5), 100);

        cache.put("abc", "https://example.com");

        assertThat(cache.get("abc")).contains("https://example.com");
        assertThat(cache.get("missing")).isEqualTo(Optional.empty());
    }

    @Test
    void clearRemovesAll() {
        LocalUrlCache cache = new LocalUrlCache(Duration.ofMinutes(5), 100);
        cache.put("a", "https://a.com");
        cache.put("b", "https://b.com");

        cache.clear();

        assertThat(cache.get("a")).isEmpty();
        assertThat(cache.get("b")).isEmpty();
    }

    @Test
    void expiresAfterTtl() throws InterruptedException {
        LocalUrlCache cache = new LocalUrlCache(Duration.ofMillis(50), 100);
        cache.put("abc", "https://example.com");

        Thread.sleep(200);

        // Caffeine 은 만료를 지연 처리할 수 있어 get 으로 확인한다.
        assertThat(cache.get("abc")).isEmpty();
    }

    @Test
    void rejectsInvalidConstruction() {
        assertThatThrownBy(() -> new LocalUrlCache(Duration.ZERO, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocalUrlCache(Duration.ofMinutes(1), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
