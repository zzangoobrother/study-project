package com.loopers.application.order

import com.loopers.infrastructure.product.PessimisticLockStockDecreaseStrategy
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = ["loopers.stock.lock-strategy=pessimistic"])
class PessimisticLockOrderFacadeConcurrencyTest : AbstractOrderFacadeConcurrencyTest() {
    override val expectedStrategy = PessimisticLockStockDecreaseStrategy::class
}
