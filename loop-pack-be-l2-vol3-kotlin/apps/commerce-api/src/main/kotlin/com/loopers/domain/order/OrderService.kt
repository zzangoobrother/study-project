package com.loopers.domain.order

import com.loopers.domain.support.PageResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 주문 애그리거트의 유스케이스.
 *
 * 이 서비스는 상품도 회원도 모른다. 재고를 차감하는 것은 이 애그리거트의 일이 아니며,
 * 세 애그리거트를 잇는 책임은 OrderFacade 에만 있다. (설계 문서 7.2 장)
 *
 * 그래서 place 가 받는 것이 productId 가 아니라 이미 스냅샷이 채워진 OrderItemModel 이다.
 * 상품을 조회해 이름과 가격을 읽는 일은 호출자가 끝내고 들어온다.
 */
@Component
class OrderService(
    private val orderRepository: OrderRepository,
) {
    @Transactional
    fun place(userId: Long, items: List<OrderItemModel>): OrderModel {
        return orderRepository.save(OrderModel.create(userId = userId, items = items))
    }

    /** 없으면 null 이다. 남의 주문인지 판정하는 것은 이 계층의 일이 아니다. */
    @Transactional(readOnly = true)
    fun getOrder(id: Long): OrderModel? {
        return orderRepository.findById(id)
    }

    @Transactional(readOnly = true)
    fun getOrders(criteria: OrderCriteria.Search): PageResult<OrderModel> {
        return orderRepository.findAll(criteria)
    }
}
