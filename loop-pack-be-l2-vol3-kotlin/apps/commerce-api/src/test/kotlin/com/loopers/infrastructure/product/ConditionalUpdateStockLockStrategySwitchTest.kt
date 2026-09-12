package com.loopers.infrastructure.product

import com.loopers.domain.product.StockDecreaseStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * loopers.stock.lock-strategy 가 실제로 대응하는 빈 하나만 올리는지 확인한다.
 * 기동 시 스위치가 문서(2026-09-09 설계 문서 6.1 장)의 약속대로 동작하는지의 유일한 자동 검증이다.
 * 태스크 2 · 3 이 낙관적 락 · 비관적 락 버전을 나란히 추가한다.
 */
@SpringBootTest
class ConditionalUpdateStockLockStrategySwitchTest @Autowired constructor(
    private val stockDecreaseStrategy: StockDecreaseStrategy,
) {
    @DisplayName("설정을 지정하지 않으면(기본값), 조건부 UPDATE 전략이 올라온다.")
    @Test
    fun loadsConditionalUpdateStrategy_byDefault() {
        assertThat(stockDecreaseStrategy).isInstanceOf(ConditionalUpdateStockDecreaseStrategy::class.java)
    }
}
