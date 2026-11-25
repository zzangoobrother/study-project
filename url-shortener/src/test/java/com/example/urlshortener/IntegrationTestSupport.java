package com.example.urlshortener;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
abstract class IntegrationTestSupport {

    // 모든 하위 테스트 클래스가 공유하는 MySQL 컨테이너 (싱글톤 패턴)
    // @Container 는 테스트 클래스마다 컨테이너를 재시작해 포트가 바뀌는데,
    // Spring 컨텍스트 캐시는 이전 포트를 그대로 들고 있어 두 번째 클래스부터 연결이 실패한다.
    // JVM 종료 시 Ryuk 가 정리하므로 한 번만 띄우고 직접 stop 하지 않는다.
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    // Redis 도 같은 이유로 싱글톤으로 띄운다.
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4").withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private LocalUrlCache localUrlCache;

    // 테스트가 DB 행을 지워도 캐시는 남아 다음 테스트를 오염시키므로(스펙 6절) 매번 L1·L2 를 비운다
    @BeforeEach
    void clearCaches() {
        var keys = redis.keys(RedisUrlCache.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        localUrlCache.clear();
    }
}
