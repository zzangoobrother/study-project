package com.loopers.domain.order

import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductName
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class OrderModelTest {
    private fun item(productId: Long = 1L, price: Long = 10_000, quantity: Int = 1) =
        OrderItemModel.create(
            productId = productId,
            productName = ProductName("상품 $productId"),
            unitPrice = Price(price),
            quantity = Quantity(quantity),
        )

    @DisplayName("주문 항목을 만들 때, ")
    @Nested
    inner class CreateItem {
        @DisplayName("소계는 단가 곱하기 수량이다.")
        @Test
        fun subtotalIsUnitPriceTimesQuantity() {
            assertThat(item(price = 10_000, quantity = 3).subtotal.value).isEqualTo(30_000L)
        }

        @DisplayName("상품 ID 가 양수가 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenProductIdIsNotPositive() {
            val result = assertThrows<CoreException> { item(productId = 0) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("주문을 만들 때, ")
    @Nested
    inner class CreateOrder {
        @DisplayName("총액은 항목 소계의 합이다.")
        @Test
        fun totalPriceIsSumOfSubtotals() {
            // arrange
            val items = listOf(
                item(productId = 1, price = 10_000, quantity = 2),
                item(productId = 2, price = 5_000, quantity = 3),
            )

            // act
            val order = OrderModel.create(userId = 1L, items = items)

            // assert
            assertThat(order.totalPrice.value).isEqualTo(35_000L)
        }

        @DisplayName("항목 수는 상품 종류의 개수다.")
        @Test
        fun itemCountIsNumberOfDistinctItems() {
            // arrange
            val items = listOf(item(productId = 1, quantity = 5), item(productId = 2, quantity = 1))

            // act
            val order = OrderModel.create(userId = 1L, items = items)

            // assert — 수량 합(6)이 아니라 항목 수(2)다
            assertThat(order.itemCount).isEqualTo(2)
        }

        @DisplayName("항목이 비어 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenItemsAreEmpty() {
            val result = assertThrows<CoreException> { OrderModel.create(userId = 1L, items = emptyList()) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("회원 ID 가 양수가 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenUserIdIsNotPositive() {
            val result = assertThrows<CoreException> { OrderModel.create(userId = 0L, items = listOf(item())) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("항목 목록을 밖에서 바꿔도, 주문 안의 항목은 그대로다.")
        @Test
        fun itemsAreNotAffectedByExternalMutation() {
            // arrange
            val source = mutableListOf(item(productId = 1))
            val order = OrderModel.create(userId = 1L, items = source)

            // act
            source.add(item(productId = 2))

            // assert
            assertAll(
                { assertThat(order.items).hasSize(1) },
                { assertThat(order.itemCount).isEqualTo(1) },
            )
        }

        @DisplayName("할인을 주지 않으면, 할인액이 0 이고 결제액은 총액과 같다.")
        @Test
        fun hasZeroDiscount_whenNotGiven() {
            // arrange
            val items = listOf(item(productId = 1L, price = 10_000, quantity = 2))

            // act
            val order = OrderModel.create(userId = 1L, items = items)

            // assert
            assertAll(
                { assertThat(order.totalPrice).isEqualTo(Price(20_000)) },
                { assertThat(order.discountAmount).isEqualTo(Price.ZERO) },
                { assertThat(order.paidAmount).isEqualTo(Price(20_000)) },
                { assertThat(order.usedCouponId).isNull() },
            )
        }

        @DisplayName("할인을 주면, 결제액이 총액에서 할인액을 뺀 값이다.")
        @Test
        fun subtractsDiscountFromTotal() {
            // arrange
            val items = listOf(item(productId = 1L, price = 10_000, quantity = 2))

            // act
            val order = OrderModel.create(
                userId = 1L,
                items = items,
                discountAmount = Price(5_000),
                usedCouponId = 7L,
            )

            // assert
            assertAll(
                { assertThat(order.totalPrice).isEqualTo(Price(20_000)) },
                { assertThat(order.discountAmount).isEqualTo(Price(5_000)) },
                { assertThat(order.paidAmount).isEqualTo(Price(15_000)) },
                { assertThat(order.usedCouponId).isEqualTo(7L) },
            )
        }

        @DisplayName("할인액이 총액과 같으면, 결제액이 0 원이다.")
        @Test
        fun paysZero_whenDiscountEqualsTotal() {
            // arrange
            val items = listOf(item(productId = 1L, price = 10_000, quantity = 1))

            // act
            val order = OrderModel.create(userId = 1L, items = items, discountAmount = Price(10_000))

            // assert
            assertThat(order.paidAmount).isEqualTo(Price.ZERO)
        }

        @DisplayName("할인액이 총액보다 크면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenDiscountExceedsTotal() {
            // arrange
            // DiscountType.calculate 가 이미 총액까지로 잘라 주지만, 애그리거트가 스스로도 막는다.
            // 이 불변식이 없으면 paidAmount 가 음수인 주문이 저장될 수 있다.
            val items = listOf(item(productId = 1L, price = 10_000, quantity = 1))

            // act
            val result = assertThrows<CoreException> {
                OrderModel.create(userId = 1L, items = items, discountAmount = Price(10_001))
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
