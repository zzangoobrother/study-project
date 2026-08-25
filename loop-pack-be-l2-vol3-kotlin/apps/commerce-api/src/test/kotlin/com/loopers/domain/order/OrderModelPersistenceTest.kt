package com.loopers.domain.order

import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductName
import com.loopers.utils.DatabaseCleanUp
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate

/**
 * 저장과 조회 단언을 **한 트랜잭션 안에서** 한다 (Ruling T4-1).
 *
 * `orderItems` 는 `fetch = FetchType.LAZY` 이고 이 프로젝트는 `open-in-view: false` 다.
 * 트랜잭션이 끝나면 영속성 컨텍스트도 함께 닫히는데, 그 뒤에는 지연 컬렉션을 초기화할 세션이 없다
 * (`LazyInitializationException: could not initialize proxy - no Session`).
 * 그래서 저장·조회·단언을 분리된 트랜잭션으로 나누면(먼저 시도했던 구조) 이 테스트 자체가 성립하지 않는다.
 *
 * `flush()` 로 DB 에 반영하고 `clear()` 로 1차 캐시를 비운 뒤 같은 트랜잭션 안에서 다시 `find()` 하면
 * 캐시된 객체가 아니라 실제 DB 값을 읽는다는 검증력은 그대로 유지된다 — 잃는 것은 "트랜잭션 종료 후 접근"
 * 뿐인데, 그건 프로덕션에서도 하지 않는 동작이다 (파사드가 `@Transactional` 이라 조회와 매핑이 같은
 * 트랜잭션 안에서 끝난다). `ProductLikeModelPersistenceTest.persistsColumns_andRestoresThem` 이
 * 이미 이 프로젝트의 표준으로 쓰는 `flush → clear → find` 패턴과 같다.
 */
@SpringBootTest
class OrderModelPersistenceTest @Autowired constructor(
    private val entityManager: EntityManager,
    private val transactionTemplate: TransactionTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun order(userId: Long = 1L) = OrderModel.create(
        userId = userId,
        items = listOf(
            OrderItemModel.create(
                productId = 1L,
                productName = ProductName("운동화"),
                unitPrice = Price(39_000),
                quantity = Quantity(2),
            ),
        ),
    )

    /**
     * cascade = ALL 이 없으면 주문만 저장되고 항목은 transient 로 남아 저장 시점에 예외가 난다.
     * 이 테스트는 "항목을 따로 저장하지 않아도 된다" 는 계약을 고정한다.
     */
    @DisplayName("주문을 저장하면, 항목이 함께 저장되고 order_id 가 채워진다.")
    @Test
    fun savesItemsTogether_whenOrderIsSaved() {
        transactionTemplate.execute {
            // arrange & act — 항목을 따로 persist 하지 않는다
            val o = order()
            entityManager.persist(o)
            entityManager.flush()
            entityManager.clear()

            // assert — 1차 캐시를 비운 뒤 같은 트랜잭션 안에서 다시 읽는다
            val found = entityManager.find(OrderModel::class.java, o.id)

            assertAll(
                { assertThat(found.items).hasSize(1) },
                { assertThat(found.items.first().productName.value).isEqualTo("운동화") },
                { assertThat(found.items.first().unitPrice.value).isEqualTo(39_000L) },
                { assertThat(found.items.first().quantity.value).isEqualTo(2) },
            )
        }
    }

    /**
     * 스냅샷 컬럼이 상품 테이블이 아니라 order_items 에서 읽히는지 확인한다.
     * 조인해서 채우고 있다면 상품이 없는 이 테스트에서 값이 비어야 한다.
     */
    @DisplayName("상품 행이 없어도, 항목의 스냅샷 값은 그대로 읽힌다.")
    @Test
    fun readsSnapshot_whenProductRowDoesNotExist() {
        transactionTemplate.execute {
            // arrange — products 테이블에 아무것도 넣지 않는다
            val o = order()
            entityManager.persist(o)
            entityManager.flush()
            entityManager.clear()

            // act
            val found = entityManager.find(OrderModel::class.java, o.id)

            // assert
            assertAll(
                { assertThat(found.totalPrice.value).isEqualTo(78_000L) },
                { assertThat(found.itemCount).isEqualTo(1) },
                { assertThat(found.items.first().productName.value).isEqualTo("운동화") },
            )
        }
    }
}
