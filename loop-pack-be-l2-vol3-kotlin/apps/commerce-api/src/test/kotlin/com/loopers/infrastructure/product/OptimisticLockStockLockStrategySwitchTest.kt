package com.loopers.infrastructure.product

import com.loopers.domain.product.StockDecreaseStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = ["loopers.stock.lock-strategy=optimistic"])
class OptimisticLockStockLockStrategySwitchTest @Autowired constructor(
    private val stockDecreaseStrategy: StockDecreaseStrategy,
) {
    @DisplayName("optimistic 으로 설정하면, 낙관적 락 전략이 올라온다.")
    @Test
    fun loadsOptimisticStrategy() {
        assertThat(stockDecreaseStrategy).isInstanceOf(OptimisticLockStockDecreaseStrategy::class.java)
    }
}
