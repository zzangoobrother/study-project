package com.loopers.domain.payment

import java.time.ZonedDateTime

/** 결제는 소프트 삭제하지 않는다. 결제 기록은 지우는 대상이 아니다. */
interface PaymentRepository {
    fun save(payment: PaymentModel): PaymentModel

    fun findById(id: Long): PaymentModel?

    fun findByTransactionKey(transactionKey: String): PaymentModel?

    /** 진행 중(REQUESTED · PENDING) 결제. 주문당 최대 1 건이다. (2026-10-13 결제 설계 3.3 장) */
    fun findInProgressByOrderId(orderId: Long): PaymentModel?

    fun findAllByOrderId(orderId: Long): List<PaymentModel>

    /** 진행 중이면서 updatedBefore 전에 마지막으로 바뀐 결제를 id 오름차순으로 limit 건. (설계 5.3 장) */
    fun findSyncTargets(updatedBefore: ZonedDateTime, limit: Int): List<PaymentModel>
}
