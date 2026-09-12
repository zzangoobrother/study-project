package com.loopers.application.order

import com.loopers.infrastructure.product.OptimisticLockStockDecreaseStrategy
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.boot.test.context.SpringBootTest

/**
 * 낙관적 락의 동시성 계약.
 *
 * AbstractOrderFacadeConcurrencyTest 의 3 건은 "재고가 허락하는 한 전부 성사된다" 를 단언하는데,
 * 재시도 상한이 있는 낙관적 락은 그것을 지키지 못한다. 백오프 없이 10 스레드가 한 행을 다투면
 * 라운드마다 승자가 1 명이므로 상한 3 회 안에 성사되는 것은 최대 3 건이다 — 재고가 넉넉해도 그렇다.
 * (2026-09-09 설계 문서 3.2 장 · 4.1 장)
 *
 * 그래서 여기서는 전략과 무관하게 지켜져야 하는 것만 단언한다 — 초과 판매가 없고, 회계가 맞고,
 * 실패는 전부 CONFLICT 다.
 */
@SpringBootTest(properties = ["loopers.stock.lock-strategy=optimistic"])
class OptimisticLockOrderFacadeConcurrencyTest : AbstractOrderFacadeConcurrencySupport() {
    override val expectedStrategy = OptimisticLockStockDecreaseStrategy::class

    /** 재시도가 소진돼 실패하는 것은 허용되지만, 재고보다 많이 팔리는 것은 허용되지 않는다. */
    @DisplayName("재시도가 소진돼 일부가 실패해도, 초과 판매는 일어나지 않는다.")
    @Test
    fun doesNotOversell_whenRetriesAreExhausted() {
        // arrange
        val stock = (CONCURRENT_USERS - 1).toLong()
        val users = (1..CONCURRENT_USERS).map { signUp("user$it") }
        val product = saveProduct(stock = stock)

        // act
        val failures = runConcurrently(CONCURRENT_USERS) { index -> place(users[index].loginId, product.id to 1) }

        // assert
        val succeeded = (CONCURRENT_USERS - failures.size).toLong()
        assertAll(
            { assertThat(failures).allMatch { it is CoreException && it.errorType == ErrorType.CONFLICT } },
            { assertThat(succeeded).describedAs("재고보다 많이 팔릴 수 없다").isLessThanOrEqualTo(stock) },
            {
                assertThat(stockOf(product.id))
                    .describedAs("재고는 성사된 건수만큼만 줄어야 한다")
                    .isEqualTo(stock - succeeded)
            },
        )
    }

    /**
     * 이 단언이 낙관적 락의 계약을 고정한다. 재고가 넉넉해 논리적 충돌이 전혀 없는데도 순수 락
     * 경합만으로 일부가 실패한다 — 이것이 이 전략의 비용이며 다른 두 전략에는 없다.
     * 실패가 사라진다면 재시도 상한이나 백오프가 바뀐 것이므로 측정 조건이 달라진 것이다.
     * (재시도 상한 3 · 백오프 없음 — 2026-09-09 설계 문서 5.1 장의 공정성 조건)
     */
    @DisplayName("재고가 넉넉해도, 경합이 재시도 상한을 넘기면 일부가 CONFLICT 로 실패한다.")
    @Test
    fun failsSomeRequests_whenContentionExceedsRetryLimit() {
        // arrange
        val initialStock = 100L
        val users = (1..CONCURRENT_USERS).map { signUp("user$it") }
        val product = saveProduct(stock = initialStock)

        // act
        val failures = runConcurrently(CONCURRENT_USERS) { index -> place(users[index].loginId, product.id to 1) }

        // assert
        assertAll(
            { assertThat(failures).describedAs("재고가 남아도 경합만으로 실패가 난다").isNotEmpty() },
            { assertThat(failures).allMatch { it is CoreException && it.errorType == ErrorType.CONFLICT } },
            {
                assertThat(stockOf(product.id))
                    .describedAs("실패한 요청은 재고를 건드리지 않는다")
                    .isEqualTo(initialStock - (CONCURRENT_USERS - failures.size))
            },
        )
    }
}
