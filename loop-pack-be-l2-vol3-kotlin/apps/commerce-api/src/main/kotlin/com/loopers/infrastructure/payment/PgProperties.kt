package com.loopers.infrastructure.payment

import org.springframework.boot.context.properties.ConfigurationProperties

/** PG 연결 정보. 타임아웃 · 재시도 · 서킷 수치는 여기가 아니라 Feign · Resilience4j 설정에 있다. (2026-10-13 결제 설계 6 장) */
@ConfigurationProperties(prefix = "pg")
data class PgProperties(
    val baseUrl: String,
    val callbackUrl: String,
)
