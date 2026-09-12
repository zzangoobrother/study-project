package com.loopers.domain.product

import com.loopers.infrastructure.product.ConditionalUpdateStockDecreaseStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = ["loopers.stock.lock-strategy=conditional-update"])
class ConditionalUpdateStockDecreaseContractTest : AbstractStockDecreaseContractTest() {
    override val expectedStrategy = ConditionalUpdateStockDecreaseStrategy::class

    @DisplayName("차감은 updated_at 을 건드리지 않는다.")
    @Test
    fun doesNotTouchUpdatedAt() {
        // arrange
        val product = saveProduct(stock = 10)
        val before = productRepository.findById(product.id)!!.updatedAt

        // act
        decreaseStock(product.id, 1)

        // assert — raw UPDATE 문은 BaseEntity.preUpdate 콜백을 타지 않는다 (2026-08-24 설계 문서 6.3 장)
        assertThat(productRepository.findById(product.id)!!.updatedAt).isEqualTo(before)
    }
}
