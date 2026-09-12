package com.loopers.domain.product

import com.loopers.infrastructure.product.OptimisticLockStockDecreaseStrategy
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = ["loopers.stock.lock-strategy=optimistic"])
class OptimisticLockStockDecreaseContractTest : AbstractStockDecreaseContractTest() {
    override val expectedStrategy = OptimisticLockStockDecreaseStrategy::class
}
