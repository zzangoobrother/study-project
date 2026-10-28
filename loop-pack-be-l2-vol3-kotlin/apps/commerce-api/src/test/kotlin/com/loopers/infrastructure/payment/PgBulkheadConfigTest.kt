package com.loopers.infrastructure.payment

import io.github.resilience4j.bulkhead.BulkheadRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration

/**
 * 운영 상한(50 · 20 · 대기 0)을 지키는 테스트다. 덮어쓰기 없는 컨텍스트라야 yml 값이 그대로 읽히므로
 * 상한을 2 로 덮어쓰는 PgPaymentGatewayBulkheadTest 와 클래스를 나눴다. (2026-10-29 PG 격리 설계 3 · 6 장)
 */
@SpringBootTest
class PgBulkheadConfigTest @Autowired constructor(
    private val bulkheadRegistry: BulkheadRegistry,
) {
    @DisplayName("운영 설정은, ")
    @Nested
    inner class Limits {
        @DisplayName("결제 요청 50 · 조회 20 이고, 둘 다 기다리지 않는다.")
        @Test
        fun usesSpecifiedLimits() {
            // arrange
            val request = bulkheadRegistry.bulkhead("pgRequest").bulkheadConfig
            val query = bulkheadRegistry.bulkhead("pgQuery").bulkheadConfig

            // assert
            assertAll(
                { assertThat(request.maxConcurrentCalls).isEqualTo(50) },
                { assertThat(request.maxWaitDuration).isEqualTo(Duration.ZERO) },
                { assertThat(query.maxConcurrentCalls).isEqualTo(20) },
                { assertThat(query.maxWaitDuration).isEqualTo(Duration.ZERO) },
            )
        }
    }
}
