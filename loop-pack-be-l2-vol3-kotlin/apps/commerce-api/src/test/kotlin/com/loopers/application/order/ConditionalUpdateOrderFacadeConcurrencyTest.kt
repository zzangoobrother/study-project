package com.loopers.application.order

import com.loopers.infrastructure.product.ConditionalUpdateStockDecreaseStrategy
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = ["loopers.stock.lock-strategy=conditional-update"])
class ConditionalUpdateOrderFacadeConcurrencyTest : AbstractOrderFacadeConcurrencyTest() {
    override val expectedStrategy = ConditionalUpdateStockDecreaseStrategy::class
}
