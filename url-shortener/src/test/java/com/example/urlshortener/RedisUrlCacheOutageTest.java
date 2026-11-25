package com.example.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 닫힌 포트를 가리키는 별도 연결 팩토리를 쓰므로 공유 Redis 컨테이너에는 영향이 없다. */
class RedisUrlCacheOutageTest {

    private LettuceConnectionFactory factory;
    private RedisUrlCache cache;

    @BeforeEach
    void setUp() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(200))
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofMillis(200)).build())
                        .build())
                .build();
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", port), clientConfig);
        factory.afterPropertiesSet();
        cache = new RedisUrlCache(new StringRedisTemplate(factory), Duration.ofDays(1), Duration.ofSeconds(60));
    }

    @AfterEach
    void tearDown() {
        factory.destroy();
    }

    @Test
    void getReturnsMissWithoutThrowing() {
        assertThat(cache.get("abc")).isInstanceOf(RedisCacheResult.Miss.class);
    }

    @Test
    void putsDoNotThrow() {
        assertThatCode(() -> {
            cache.putFound("abc", "https://example.com");
            cache.putNotFound("abc");
        }).doesNotThrowAnyException();
    }

    @Test
    void failsFastWithinTimeout() {
        long start = System.nanoTime();
        cache.get("abc");
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(elapsed).as("연결 거부 시 get 소요 시간").isLessThan(Duration.ofSeconds(2));
    }
}
