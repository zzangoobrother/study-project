package com.loopers.domain.order

import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductName
import com.loopers.domain.support.PageQuery
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

@SpringBootTest
class OrderServiceIntegrationTest @Autowired constructor(
    private val orderService: OrderService,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun item(productId: Long = 1L, name: String = "운동화", price: Long = 10_000, quantity: Int = 1) =
        OrderItemModel.create(
            productId = productId,
            productName = ProductName(name),
            unitPrice = Price(price),
            quantity = Quantity(quantity),
        )

    private fun place(userId: Long = 1L, vararg items: OrderItemModel) =
        orderService.place(userId = userId, items = items.toList())

    private fun search(userId: Long = 1L, startAt: LocalDate? = null, endAt: LocalDate? = null, size: Int = 20) =
        OrderCriteria.Search(userId = userId, startAt = startAt, endAt = endAt, pageQuery = PageQuery(size = size))

    /**
     * 이 클래스의 두 테스트는 `orderService.getOrder(...)!!.items` 를 트랜잭션 밖(테스트 메서드 본문)에서
     * 읽는다. 우연이 아니라 의도다 — getOrder 는 호출자에게 별도 트랜잭션 없이도 완결된 애그리거트를
     * 준다는 계약이고, 주문 상세(Task 8/9)가 실제로 이렇게 쓴다. (Ruling T5-1)
     *
     * 이 단언이 이 자리에 있는 것 자체가 회귀 방어선이다. `OrderQueryDslRepository.findById` 의
     * fetch join 이 빠지면 orderItems 가 다시 LAZY 로 남아 여기서 LazyInitializationException 이 난다.
     */
    @DisplayName("주문을 저장할 때, ")
    @Nested
    inner class Place {
        @DisplayName("항목이 함께 저장되고 총액과 항목 수가 채워진다.")
        @Test
        fun savesItemsAndTotals() {
            // act
            val order = place(items = arrayOf(item(productId = 1, price = 10_000, quantity = 2), item(productId = 2, price = 5_000)))

            // assert
            val found = orderService.getOrder(order.id)!!
            assertAll(
                { assertThat(found.items).hasSize(2) },
                { assertThat(found.totalPrice.value).isEqualTo(25_000L) },
                { assertThat(found.itemCount).isEqualTo(2) },
            )
        }

        /**
         * 스냅샷이 스냅샷인지 확인하는 테스트다.
         * 항목이 상품을 조회해 이름을 채우고 있다면 상품이 없는 이 테스트에서 값이 비어야 한다.
         */
        @DisplayName("상품 행이 없어도, 항목의 상품명과 단가가 그대로 남는다.")
        @Test
        fun keepsSnapshot_whenProductRowDoesNotExist() {
            // act
            val order = place(items = arrayOf(item(productId = 99999, name = "사라진 상품", price = 7_000)))

            // assert
            val found = orderService.getOrder(order.id)!!.items.first()
            assertAll(
                { assertThat(found.productName.value).isEqualTo("사라진 상품") },
                { assertThat(found.unitPrice.value).isEqualTo(7_000L) },
            )
        }
    }

    @DisplayName("주문을 단건 조회할 때, ")
    @Nested
    inner class GetOrder {
        @DisplayName("없는 ID 면, null 을 반환한다.")
        @Test
        fun returnsNull_whenOrderDoesNotExist() {
            assertThat(orderService.getOrder(99999L)).isNull()
        }
    }

    @DisplayName("주문 목록을 조회할 때, ")
    @Nested
    inner class GetOrders {
        @DisplayName("최근 주문이 앞에 온다.")
        @Test
        fun ordersByMostRecent() {
            // arrange
            val first = place(items = arrayOf(item(productId = 1)))
            val second = place(items = arrayOf(item(productId = 2)))

            // act
            val result = orderService.getOrders(search())

            // assert
            assertThat(result.content.map { it.id }).containsExactly(second.id, first.id)
        }

        @DisplayName("다른 회원의 주문은 섞이지 않는다.")
        @Test
        fun doesNotMixOtherUsersOrders() {
            // arrange
            val mine = place(userId = 1L, items = arrayOf(item(productId = 1)))
            place(userId = 2L, items = arrayOf(item(productId = 2)))

            // act
            val result = orderService.getOrders(search(userId = 1L))

            // assert
            assertAll(
                { assertThat(result.content.map { it.id }).containsExactly(mine.id) },
                { assertThat(result.totalElements).isEqualTo(1L) },
            )
        }

        @DisplayName("기간을 생략하면, 전체가 조회된다.")
        @Test
        fun returnsAll_whenPeriodIsOmitted() {
            // arrange
            place(items = arrayOf(item(productId = 1)))
            place(items = arrayOf(item(productId = 2)))

            // act
            val result = orderService.getOrders(search())

            // assert
            assertThat(result.totalElements).isEqualTo(2L)
        }

        /**
         * endAt 은 그날을 포함한다. (설계 문서 4.4 장)
         * created_at <= endAt 00:00 으로 구현하면 오늘 주문한 건이 오늘을 endAt 으로 줘도 빠진다.
         */
        @DisplayName("오늘을 endAt 으로 주면, 오늘 주문이 포함된다.")
        @Test
        fun includesToday_whenEndAtIsToday() {
            // arrange
            place(items = arrayOf(item(productId = 1)))

            // act
            val result = orderService.getOrders(search(startAt = LocalDate.now(), endAt = LocalDate.now()))

            // assert
            assertThat(result.totalElements).isEqualTo(1L)
        }

        @DisplayName("기간 밖이면, 결과에서 빠진다.")
        @Test
        fun excludesOutOfPeriod() {
            // arrange
            place(items = arrayOf(item(productId = 1)))
            val yesterday = LocalDate.now().minusDays(1)

            // act
            val result = orderService.getOrders(search(startAt = yesterday.minusDays(7), endAt = yesterday))

            // assert
            assertThat(result.totalElements).isEqualTo(0L)
        }

        @DisplayName("페이징 메타가 주문 개수를 기준으로 채워진다.")
        @Test
        fun fillsPagingMetadata() {
            // arrange
            repeat(5) { index -> place(items = arrayOf(item(productId = (index + 1).toLong()))) }

            // act
            val result = orderService.getOrders(
                OrderCriteria.Search(userId = 1L, startAt = null, endAt = null, pageQuery = PageQuery(page = 1, size = 2)),
            )

            // assert
            assertAll(
                { assertThat(result.content).hasSize(2) },
                { assertThat(result.page).isEqualTo(1) },
                { assertThat(result.size).isEqualTo(2) },
                { assertThat(result.totalElements).isEqualTo(5L) },
                { assertThat(result.totalPages).isEqualTo(3) },
            )
        }
    }
}
