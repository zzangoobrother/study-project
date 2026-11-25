package com.example.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 닫힌 포트를 가리키는 Redis 로도 조회와 생성이 DB 만으로 성공하는지 검증한다. */
class ShortUrlLookupOutageTest {

    private static final String URL = "https://example.com/outage";

    private LettuceConnectionFactory factory;
    private ShortUrlRepository repo;
    private ShortUrlLookup lookup;

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
        RedisUrlCache l2 = new RedisUrlCache(new StringRedisTemplate(factory), Duration.ofDays(1), Duration.ofSeconds(60));

        repo = mock(ShortUrlRepository.class);
        lookup = new ShortUrlLookup(new LocalUrlCache(Duration.ofMinutes(5), 1000), l2, repo);
    }

    @AfterEach
    void tearDown() {
        factory.destroy();
    }

    @Test
    void servesFromDbWhenRedisDown() {
        when(repo.findByShortKey("exists")).thenReturn(Optional.of(ShortUrl.create(URL, "exists")));
        when(repo.findByShortKey("missing")).thenReturn(Optional.empty());

        assertThat(lookup.findOriginalUrl("exists")).contains(URL);
        assertThat(lookup.findOriginalUrl("missing")).isEmpty();
    }

    @Test
    void recordCreatedDoesNotThrowWhenRedisDown() {
        assertThatCode(() -> lookup.recordCreated("abc", URL)).doesNotThrowAnyException();
    }
}
