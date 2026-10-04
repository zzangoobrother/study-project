package com.loopers.testcontainers

import com.redis.testcontainers.RedisContainer
import org.springframework.context.annotation.Configuration

@Configuration
class RedisTestContainersConfig {
    companion object {
        private val redisContainer = RedisContainer("redis:latest")
            .apply {
                start()
            }

        // 접속 정보는 RedisProperties 가 바인딩되기 전에 설정해야 한다. 인스턴스 init 은 바인딩보다 늦게 실행될 수 있고,
        // 그러면 테스트가 조용히 localhost:6379 로 접속한다. (2026-10-04 상품 캐시 설계 9.4 장)
        init {
            System.setProperty("datasource.redis.database", "0")
            System.setProperty("datasource.redis.master.host", redisContainer.host)
            System.setProperty("datasource.redis.master.port", redisContainer.firstMappedPort.toString())
            System.setProperty("datasource.redis.replicas[0].host", redisContainer.host)
            System.setProperty("datasource.redis.replicas[0].port", redisContainer.firstMappedPort.toString())
        }
    }
}
