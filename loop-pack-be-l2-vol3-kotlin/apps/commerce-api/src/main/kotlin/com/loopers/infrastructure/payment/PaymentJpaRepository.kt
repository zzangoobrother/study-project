package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.ZonedDateTime

interface PaymentJpaRepository : JpaRepository<PaymentModel, Long> {
    fun findByTransactionKey(transactionKey: String): PaymentModel?

    fun findFirstByOrderIdAndStatusIn(orderId: Long, statuses: Collection<PaymentStatus>): PaymentModel?

    fun findAllByOrderId(orderId: Long): List<PaymentModel>

    /** idx_payments_status_updated_at 을 탄다. */
    fun findByStatusInAndUpdatedAtBeforeOrderByIdAsc(
        statuses: Collection<PaymentStatus>,
        updatedAt: ZonedDateTime,
        pageable: Pageable,
    ): List<PaymentModel>
}
