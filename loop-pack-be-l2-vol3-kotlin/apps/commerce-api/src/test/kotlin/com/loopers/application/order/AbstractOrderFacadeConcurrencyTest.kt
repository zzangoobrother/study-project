package com.loopers.application.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

/**
 * 기다렸다 성공하는 전략의 동시성 계약. (2026-09-09 설계 문서 3.2 장)
 *
 * 조건부 UPDATE 와 비관적 락은 경합하면 행 락에서 기다렸다가 성공하므로, 보낸 요청이 재고가
 * 허락하는 한 전부 성사된다. 낙관적 락은 이 계약을 지키지 못하므로 상속하지 않는다 —
 * updated_at 을 공통 계약에서 뺀 것과 같은 종류의 분기이며, 구현의 실수가 아니라 기법의 성질이다.
 *
 * 테스트 3 건의 본문은 조건부 UPDATE 에서 통과가 확인된 회귀 방지선이므로 한 글자도 바꾸지 않는다.
 *
 * Testcontainers 가 띄우는 진짜 MySQL 8.0 위에서 돌기 때문에 InnoDB 의 행 락과 데드락 감지가 실제로 동작한다.
 * 인메모리 DB 였다면 이 검증이 불가능했을 것이다.
 *
 * 각 테스트가 재고와 주문 성사 여부를 함께 단언하는 이유는,
 * 둘이 어긋나는 것 — 재고는 줄었는데 주문이 없거나 그 반대 — 이 확인 대상이기 때문이다.
 */
abstract class AbstractOrderFacadeConcurrencyTest : AbstractOrderFacadeConcurrencySupport() {
    /**
     * 초과 판매 방지의 본체다.
     * 조건부 UPDATE 의 WHERE stock >= :quantity 를 빼면 재고가 음수가 되고 전원이 성공한다.
     * "읽고 → 빼고 → 쓰기" 로 되돌려도 마찬가지로 깨진다.
     */
    @DisplayName("재고보다 많은 회원이 동시에 주문하면, 정확히 재고만큼만 성사되고 나머지는 CONFLICT 다.")
    @Test
    fun sellsExactlyStock_whenMoreUsersOrderConcurrently() {
        // arrange — 재고 9 개에 10 명이 1 개씩 주문한다
        val stock = (CONCURRENT_USERS - 1).toLong()
        val users = (1..CONCURRENT_USERS).map { signUp("user$it") }
        val product = saveProduct(stock = stock)

        // act
        val failures = runConcurrently(CONCURRENT_USERS) { index -> place(users[index].loginId, product.id to 1) }

        // assert
        assertAll(
            { assertThat(stockOf(product.id)).describedAs("재고는 정확히 0 이어야 한다").isEqualTo(0L) },
            { assertThat(failures).describedAs("재고를 넘어선 요청 1 건만 실패해야 한다").hasSize(1) },
            {
                assertThat(failures.filterIsInstance<CoreException>().map { it.errorType })
                    .describedAs("실패는 재고 부족이어야 한다")
                    .containsExactly(ErrorType.CONFLICT)
            },
        )
    }

    /**
     * 갱신 손실이 있으면 재고가 덜 빠진다.
     * 여러 스레드가 같은 값을 읽고 같은 값을 쓰면 차감이 사라진다.
     */
    @DisplayName("여러 회원이 같은 상품을 동시에 주문하면, 차감 합계가 주문 수량 합과 정확히 일치한다.")
    @Test
    fun decreasesExactSum_whenManyUsersOrderConcurrently() {
        // arrange — 넉넉한 재고에 10 명이 2 개씩 주문한다
        val initialStock = 100L
        val users = (1..CONCURRENT_USERS).map { signUp("user$it") }
        val product = saveProduct(stock = initialStock)

        // act
        val failures = runConcurrently(CONCURRENT_USERS) { index -> place(users[index].loginId, product.id to 2) }

        // assert
        assertAll(
            { assertThat(failures).describedAs("재고가 넉넉하므로 실패가 없어야 한다").isEmpty() },
            {
                assertThat(stockOf(product.id))
                    .describedAs("정확히 주문 수량 합만큼 줄어야 한다")
                    .isEqualTo(initialStock - CONCURRENT_USERS * 2)
            },
        )
    }

    /**
     * 데드락 방어선이다. OrderFacade 의 sortedBy { it.productId } 를 지우면 이 테스트가 깨진다.
     *
     * 두 주문이 상품 A·B 를 반대 순서로 담고 동시에 들어오면, 정렬이 없을 때
     * 한쪽이 A 의 락을 잡고 B 를 기다리는 동안 다른 쪽이 B 를 잡고 A 를 기다린다.
     * InnoDB 가 이를 감지해 한쪽을 롤백시키므로 데이터는 깨지지 않지만 멀쩡한 주문이 실패한다.
     *
     * 실패 타입을 좁혀 단언하지 않는 이유는, 데드락 패배자가 받는 예외가
     * DeadlockLoserDataAccessException 이거나 그것을 감싼 다른 타입일 수 있기 때문이다.
     * "예외 없이 둘 다 성공" 이 이 테스트가 고정하려는 계약이므로 그것만 단언한다.
     */
    @DisplayName("두 주문이 상품을 반대 순서로 담아도, 데드락 없이 둘 다 성사된다.")
    @Test
    fun doesNotDeadlock_whenOrdersLockProductsInOppositeOrder() {
        // arrange
        val first = signUp("loopers01")
        val second = signUp("loopers02")
        val productA = saveProduct(name = "A", stock = 50)
        val productB = saveProduct(name = "B", stock = 50)

        // act — 홀수 스레드는 A→B, 짝수 스레드는 B→A 순서로 담는다
        val failures = runConcurrently(CONCURRENT_USERS) { index ->
            if (index % 2 == 0) {
                place(first.loginId, productA.id to 1, productB.id to 1)
            } else {
                place(second.loginId, productB.id to 1, productA.id to 1)
            }
        }

        // assert
        assertAll(
            {
                assertThat(failures)
                    .describedAs("정렬이 없으면 데드락 패배자가 여기 쌓인다")
                    .isEmpty()
            },
            { assertThat(stockOf(productA.id)).isEqualTo(50L - CONCURRENT_USERS) },
            { assertThat(stockOf(productB.id)).isEqualTo(50L - CONCURRENT_USERS) },
        )
    }
}
