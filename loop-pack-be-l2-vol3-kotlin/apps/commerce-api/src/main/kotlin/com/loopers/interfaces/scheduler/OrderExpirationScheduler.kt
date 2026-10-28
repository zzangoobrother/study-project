package com.loopers.interfaces.scheduler

import com.loopers.application.order.OrderExpirationFacade
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 결제 기한(30 분)이 지난 주문을 취소하고 재고 · 쿠폰을 되돌린다.
 *
 * interfaces 에 두는 이유와 분산 락이 없는 이유는 [PaymentSyncScheduler] 와 같다.
 * 여기서는 주문 전이가 조건부 UPDATE 라 여러 인스턴스가 같은 건을 처리해도 복구는 한 번뿐이다. (2026-10-28 주문 만료 설계 4.2 장)
 */
@Component
class OrderExpirationScheduler(
    private val orderExpirationFacade: OrderExpirationFacade,
) {
    @Scheduled(fixedDelay = 60_000)
    fun expireOverdue() {
        orderExpirationFacade.expireOverdue()
    }
}
