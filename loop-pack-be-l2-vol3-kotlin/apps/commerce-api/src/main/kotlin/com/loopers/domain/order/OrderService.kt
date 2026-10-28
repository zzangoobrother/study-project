package com.loopers.domain.order

import com.loopers.domain.product.Price
import com.loopers.domain.support.PageResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

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
    fun place(
        userId: Long,
        items: List<OrderItemModel>,
        discountAmount: Price = Price.ZERO,
        usedCouponId: Long? = null,
    ): OrderModel {
        return orderRepository.save(
            OrderModel.create(
                userId = userId,
                items = items,
                discountAmount = discountAmount,
                usedCouponId = usedCouponId,
            ),
        )
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

    @Transactional(readOnly = true)
    fun getOrdersForAdmin(criteria: OrderCriteria.AdminSearch): PageResult<OrderModel> {
        return orderRepository.findAllForAdmin(criteria)
    }

    /**
     * 결제를 시작한다. false 면 결제할 수 없는 상태(결제 중 · 완료 · 취소)의 주문이다.
     * 결제 실패 주문도 시작할 수 있다 — 다른 카드로 다시 결제하는 경로다. (2026-10-13 결제 설계 3.2 장)
     */
    @Transactional
    fun startPayment(orderId: Long): Boolean = orderRepository.changeStatus(
        orderId = orderId,
        expected = CANCELABLE,
        next = OrderStatus.PAYING,
    )

    /** false 면 다른 경로(콜백 · 스케줄러)가 먼저 확정했다. 호출자는 무시한다. */
    @Transactional
    fun completePayment(orderId: Long): Boolean =
        orderRepository.changeStatus(orderId, setOf(OrderStatus.PAYING), OrderStatus.PAID)

    /** false 면 다른 경로가 먼저 확정했다. 호출자는 무시한다. */
    @Transactional
    fun failPayment(orderId: Long): Boolean =
        orderRepository.changeStatus(orderId, setOf(OrderStatus.PAYING), OrderStatus.PAYMENT_FAILED)

    /**
     * 결제가 시작되기 전인 주문만 취소한다. false 면 결제가 시작됐거나 이미 취소됐다. 호출자는 재고 · 쿠폰을 되돌리지 않는다.
     */
    @Transactional
    fun cancel(orderId: Long): Boolean = orderRepository.changeStatus(orderId, CANCELABLE, OrderStatus.CANCELED)

    /**
     * 만료 대상 주문 ID 를 id 오름차순으로 limit 건 돌려준다. 대상 상태는 [cancel] 이 취소할 수 있는 상태와 같다.
     */
    @Transactional(readOnly = true)
    fun findExpirationTargets(createdBefore: ZonedDateTime, limit: Int): List<Long> =
        orderRepository.findExpirationTargets(CANCELABLE, createdBefore, limit)

    private companion object {
        /**
         * 취소할 수 있는 상태 = 만료 대상 상태. 둘이 어긋나면 고르고도 취소하지 못하는 주문이 매 주기 다시 뽑힌다.
         * 결제 시작이 가능한 상태이기도 하다.
         */
        val CANCELABLE = setOf(OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_FAILED)
    }
}
