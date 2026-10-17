package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {
    override fun save(payment: PaymentModel): PaymentModel = paymentJpaRepository.save(payment)

    override fun findById(id: Long): PaymentModel? = paymentJpaRepository.findByIdOrNull(id)

    override fun findByTransactionKey(transactionKey: String): PaymentModel? =
        paymentJpaRepository.findByTransactionKey(transactionKey)

    override fun findInProgressByOrderId(orderId: Long): PaymentModel? =
        paymentJpaRepository.findFirstByOrderIdAndStatusIn(orderId, PaymentStatus.IN_PROGRESS)

    override fun findAllByOrderId(orderId: Long): List<PaymentModel> = paymentJpaRepository.findAllByOrderId(orderId)

    override fun findSyncTargets(updatedBefore: ZonedDateTime, limit: Int): List<PaymentModel> =
        paymentJpaRepository.findByStatusInAndUpdatedAtBeforeOrderByIdAsc(
            statuses = PaymentStatus.IN_PROGRESS,
            updatedAt = updatedBefore,
            pageable = PageRequest.of(0, limit),
        )
}
