package com.loopers.infrastructure.product

import com.loopers.domain.product.StockDecreaseStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = ["loopers.stock.lock-strategy=pessimistic"])
class PessimisticLockStockLockStrategySwitchTest @Autowired constructor(
    private val stockDecreaseStrategy: StockDecreaseStrategy,
) {
    @DisplayName("pessimistic 으로 설정하면, 비관적 락 전략이 올라온다.")
    @Test
    fun loadsPessimisticStrategy() {
        assertThat(stockDecreaseStrategy).isInstanceOf(PessimisticLockStockDecreaseStrategy::class.java)
    }
}
