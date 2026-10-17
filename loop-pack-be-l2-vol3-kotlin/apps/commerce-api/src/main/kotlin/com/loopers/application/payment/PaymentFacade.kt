package com.loopers.application.payment

import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.PaymentCommand
import com.loopers.domain.payment.PaymentGateway
import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgPaymentRequest
import com.loopers.domain.payment.PgRequestResult
import com.loopers.domain.product.Price
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/**
 * 결제 · 주문 두 애그리거트와 외부 PG 를 잇는 유스케이스.
 *
 * @Transactional 이 아니라 TransactionTemplate 인 이유 — PG 호출을 트랜잭션 밖에 두려면 한 메서드 안에서 두 번 커밋해야 한다.
 * PG 호출을 트랜잭션 안에 두면 요청마다 DB 커넥션을 PG 응답 시간만큼 쥐고, PG 가 느려지면 커넥션 풀이 말라
 * PG 와 무관한 API 까지 멈춘다. 서킷은 실패가 쌓인 뒤에야 열리므로 이걸 막지 못한다. (2026-10-13 결제 설계 4.1 장)
 *
 * tx1 을 PG 호출 전에 커밋하는 것이 복구의 전제다. 이후 무엇이 실패해도 주문은 PAYING, 결제는 REQUESTED 로 남아 동기화가 찾아낸다.
 *
 * 주의: 이 API 는 인증하지 않는다. 로그인 ID 를 아는 누구나 남의 주문을 PAYING 으로 묶어 둘 수 있다. (설계 9.3 장)
 */
@Component
class PaymentFacade(
    private val userService: UserService,
    private val orderService: OrderService,
    private val paymentService: PaymentService,
    private val paymentGateway: PaymentGateway,
    private val transactionTemplate: TransactionTemplate,
) {
    companion object {
        private const val REASON_NOT_SENT = "결제 시스템이 불안정합니다. 잠시 후 다시 시도해주세요."
    }

    fun request(command: PaymentCommand.Request): PaymentInfo {
        val payment = transactionTemplate.execute { startPayment(command) }!!
        if (payment.status == PaymentStatus.SUCCESS) {
            return PaymentInfo.of(payment)
        }

        val result = paymentGateway.request(
            PgPaymentRequest(
                userId = payment.userId,
                orderId = payment.orderId,
                cardType = payment.cardType,
                cardNo = payment.cardNo,
                amount = payment.amount,
            ),
        )

        // tx2 와 콜백의 확정이 겹치면 늦게 커밋하는 쪽이 @Version 검사로 실패한다. 콜백이 이겼다면 그 결과가 더 최신이므로
        // 덮어쓰지 않고 현재 상태를 돌려준다. 예외를 올리면 PG 가 접수한 결제 요청이 500 이 된다. (2026-10-13 결제 설계 5.2 장)
        return try {
            transactionTemplate.execute { applyRequestResult(payment.id, result) }!!
        } catch (e: OptimisticLockingFailureException) {
            PaymentInfo.of(paymentService.getPayment(payment.id)!!)
        }
    }

    /**
     * tx1. 주문을 PAYING 으로 바꾸고 결제를 REQUESTED 로 남긴다.
     *
     * ⚠️ paidAmount 를 startPayment 보다 먼저 읽는다. 조건부 UPDATE 가 clearAutomatically 라 호출 뒤 order 가 detach 된다.
     */
    private fun startPayment(command: PaymentCommand.Request): PaymentModel {
        val user = getUserOrThrow(command.loginId)
        val order = orderService.getOrder(command.orderId)
        if (order == null || order.userId != user.id) {
            throw CoreException(ErrorType.NOT_FOUND, "[orderId = ${command.orderId}] 존재하지 않는 주문입니다.")
        }
        val amount = order.paidAmount

        if (!orderService.startPayment(order.id)) {
            throw CoreException(ErrorType.CONFLICT, "[orderId = ${order.id}] 이미 결제 중이거나 결제가 완료된 주문입니다.")
        }

        val payment = paymentService.create(
            orderId = order.id,
            userId = user.id,
            amount = amount,
            cardType = command.cardType,
            cardNo = command.cardNo,
        )

        // 쿠폰으로 전액 할인된 주문. PG 는 0 원을 400 으로 거부하므로 PG 를 거치지 않고 확정한다.
        // 막으면 그 주문은 영원히 결제할 수 없다. (설계 4.3 장)
        // succeed 를 completePayment 보다 먼저 한다 — 조건부 UPDATE 의 flush 가 이 변경을 먼저 내보낸다.
        if (amount == Price.ZERO) {
            payment.succeed()
            orderService.completePayment(order.id)
        }
        return payment
    }

    /** tx2. PG 호출 결과를 반영한다. 즉시 실패로 확정하는 것은 NotSent 뿐이다. (설계 4.2 장) */
    private fun applyRequestResult(paymentId: Long, result: PgRequestResult): PaymentInfo {
        val payment = paymentService.getPayment(paymentId)!!
        when (result) {
            is PgRequestResult.Accepted -> payment.markPending(result.transactionKey)
            PgRequestResult.NotSent -> {
                payment.fail(REASON_NOT_SENT)
                orderService.failPayment(payment.orderId)
            }
            PgRequestResult.Unknown -> Unit
        }
        return PaymentInfo.of(payment)
    }

    private fun getUserOrThrow(loginId: LoginId): UserModel =
        userService.getUser(loginId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[loginId = ${loginId.value}] 존재하지 않는 회원입니다.")
}
