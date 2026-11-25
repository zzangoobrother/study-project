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

/**
 * 응답하지 않는 Redis 를 흉내 낸다. accept() 를 호출하지 않는 ServerSocket 은
 * OS 백로그가 TCP 핸드셰이크만 끝내 주고 이후 아무 응답도 없으므로, 즉시 거부가 아닌 타임아웃 경로를 탄다.
 */
class RedisUrlCacheHungTest {

    private ServerSocket server;
    private LettuceConnectionFactory factory;
    private RedisUrlCache cache;

    @BeforeEach
    void setUp() throws IOException {
        server = new ServerSocket(0);
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(200))
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofMillis(200)).build())
                        .build())
                .build();
        factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("localhost", server.getLocalPort()), clientConfig);
        factory.afterPropertiesSet();
        cache = new RedisUrlCache(new StringRedisTemplate(factory), Duration.ofDays(1), Duration.ofSeconds(60));
    }

    @AfterEach
    void tearDown() throws IOException {
        factory.destroy();
        server.close();
    }

    @Test
    void hungRedisTimesOutNearCommandTimeout() {
        long start = System.nanoTime();
        RedisCacheResult result = cache.get("abc");
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(result).isInstanceOf(RedisCacheResult.Miss.class);
        assertThat(elapsed).as("무응답 Redis get 소요 시간").isGreaterThanOrEqualTo(Duration.ofMillis(150));
        assertThat(elapsed).as("무응답 Redis get 소요 시간").isLessThan(Duration.ofSeconds(1));
        assertThatCode(() -> {
            cache.putFound("abc", "https://example.com");
            cache.putNotFound("abc");
        }).doesNotThrowAnyException();
    }
}
