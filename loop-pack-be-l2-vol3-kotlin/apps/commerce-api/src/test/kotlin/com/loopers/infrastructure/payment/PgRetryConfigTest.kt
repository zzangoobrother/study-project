package com.loopers.infrastructure.payment

import io.github.resilience4j.core.functions.Either
import io.github.resilience4j.retry.RetryRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * 운영 재시도 대기(200 ms 기준, 2 배 백오프, ±25% jitter)를 지키는 테스트다. 덮어쓰기 없는 컨텍스트라야 yml 값이 그대로 읽힌다.
 * PgPaymentGatewayTest 는 wait-duration 을 10 ms 로 덮어쓰므로 여기서 따로 본다. (2026-10-13 결제 설계 6.2 장)
 *
 * 대기는 `retryConfig.intervalBiFunction.apply(회차, Either.left(예외))` 로 뽑는다. 회차는 "방금 실패한 시도의 번호" 로 1 부터 시작한다
 * (RetryImpl 이 numOfAttempts 를 늘린 뒤 넘긴다). 1 회차 = 첫 시도가 실패한 뒤의 대기, 2 회차 = 두 번째 시도가 실패한 뒤의 대기다.
 * 반환은 ms 다.
 */
@SpringBootTest
class PgRetryConfigTest @Autowired constructor(
    private val retryRegistry: RetryRegistry,
) {
    private fun sampleWaits(attempt: Int, count: Int = SAMPLES): List<Long> {
        val retryConfig = retryRegistry.retry("pgRequest").retryConfig
        val intervalBiFunction = retryConfig.getIntervalBiFunction<Any>()
        return List(count) { intervalBiFunction.apply(attempt, Either.left(RuntimeException())) }
    }

    @DisplayName("재시도 대기는, ")
    @Nested
    inner class Wait {
        @DisplayName("1 회차는 150–250 ms, 2 회차는 300–500 ms 안이다. (기준 200 · 400 의 ±25%)")
        @Test
        fun staysWithinJitterRange() {
            // act
            val first = sampleWaits(1)
            val second = sampleWaits(2)

            // assert
            assertAll(
                { assertThat(first).allSatisfy { assertThat(it).isBetween(150L, 250L) } },
                { assertThat(second).allSatisfy { assertThat(it).isBetween(300L, 500L) } },
            )
        }

        @DisplayName("매번 같은 값이 아니다. (jitter 가 켜져 있다)")
        @Test
        fun variesBetweenCalls() {
            // act
            val first = sampleWaits(1)

            // assert
            assertThat(first.min()).isLessThan(first.max())
        }

        @DisplayName("범위가 ±25% 폭 전체에 퍼진다. (폭이 더 좁으면 양 끝 가까운 값이 나오지 않는다)")
        @Test
        fun spreadsAcrossWholeRangeOfQuarter() {
            // act
            val first = sampleWaits(1)
            val second = sampleWaits(2)

            // assert
            // 200 번 뽑으면 균등 분포에서 끝 1/4 구간을 모두 피할 확률이 무시할 만큼 작다. 양쪽 끝 가까이 값이 나오는지로 폭이 ±25% 임을 본다.
            assertAll(
                { assertThat(first.min()).isLessThan(175L) },
                { assertThat(first.max()).isGreaterThan(225L) },
                { assertThat(second.min()).isLessThan(350L) },
                { assertThat(second.max()).isGreaterThan(450L) },
            )
        }
    }

    @DisplayName("재시도 횟수는, ")
    @Nested
    inner class Attempts {
        @DisplayName("3 회 그대로다.")
        @Test
        fun keepsMaxAttempts() {
            // arrange
            val retryConfig = retryRegistry.retry("pgRequest").retryConfig

            // assert
            assertThat(retryConfig.maxAttempts).isEqualTo(3)
        }
    }

    companion object {
        private const val SAMPLES = 200
    }
}
