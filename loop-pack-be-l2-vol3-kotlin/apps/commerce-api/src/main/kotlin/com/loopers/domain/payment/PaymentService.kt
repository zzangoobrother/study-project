package com.loopers.domain.payment

import com.loopers.domain.product.Price
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

/**
 * 결제 애그리거트의 유스케이스. 주문을 모른다 — 주문 상태와 결제 상태를 함께 바꾸는 일은 PaymentFacade 에만 있다.
 *
 * 상태 전이 메서드가 없는 이유는, 전이가 언제나 Facade 의 트랜잭션 안에서 조회한 엔티티의 메서드로 일어나고
 * 더티 체킹으로 반영되기 때문이다.
 */
@Component
class PaymentService(
    private val paymentRepository: PaymentRepository,
) {
    @Transactional
    fun create(
        orderId: Long,
        userId: Long,
        amount: Price,
        cardType: CardType,
        cardNo: CardNo,
    ): PaymentModel = paymentRepository.save(
        PaymentModel.create(orderId = orderId, userId = userId, amount = amount, cardType = cardType, cardNo = cardNo),
    )

    @Transactional(readOnly = true)
    fun getPayment(id: Long): PaymentModel? = paymentRepository.findById(id)

    @Transactional(readOnly = true)
    fun findByTransactionKey(transactionKey: String): PaymentModel? = paymentRepository.findByTransactionKey(transactionKey)

    @Transactional(readOnly = true)
    fun findInProgressByOrderId(orderId: Long): PaymentModel? = paymentRepository.findInProgressByOrderId(orderId)

    @Transactional(readOnly = true)
    fun findAllByOrderId(orderId: Long): List<PaymentModel> = paymentRepository.findAllByOrderId(orderId)

    @Transactional(readOnly = true)
    fun findSyncTargets(updatedBefore: ZonedDateTime, limit: Int): List<PaymentModel> =
        paymentRepository.findSyncTargets(updatedBefore = updatedBefore, limit = limit)
}
