package com.loopers.infrastructure.order

import com.loopers.domain.order.OrderCriteria
import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.support.PageResult
import org.springframework.stereotype.Component

@Component
class OrderRepositoryImpl(
    private val orderJpaRepository: OrderJpaRepository,
    private val orderQueryDslRepository: OrderQueryDslRepository,
) : OrderRepository {
    override fun save(order: OrderModel): OrderModel {
        return orderJpaRepository.save(order)
    }

    override fun findById(id: Long): OrderModel? {
        return orderQueryDslRepository.findById(id)
    }

    override fun findAll(criteria: OrderCriteria.Search): PageResult<OrderModel> {
        return orderQueryDslRepository.search(criteria)
    }
}
