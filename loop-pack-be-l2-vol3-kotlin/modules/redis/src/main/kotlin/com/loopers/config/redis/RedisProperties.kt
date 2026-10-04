package com.loopers.config.redis

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(value = "datasource.redis")
data class RedisProperties(
    val database: Int,
    val master: RedisNodeInfo,
    val replicas: List<RedisNodeInfo>,
    /**
     * 명령 하나가 응답을 기다리는 상한. 설정하지 않으면 Lettuce 기본값 60 초가 적용되어,
     * 응답 없는 Redis 앞에서 요청 스레드가 60 초 동안 묶인다. (2026-10-04 상품 캐시 설계 7.2 장)
     */
    val commandTimeout: Duration,
)
