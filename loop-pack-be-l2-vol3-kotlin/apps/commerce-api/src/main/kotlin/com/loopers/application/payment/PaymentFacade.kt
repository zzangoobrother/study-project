package com.loopers.application.payment

import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.PaymentCommand
import com.loopers.domain.payment.PaymentGateway
import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgPaymentRequest
import com.loopers.domain.payment.PgQueryResult
import com.loopers.domain.payment.PgRequestResult
import com.loopers.domain.payment.PgTransaction
import com.loopers.domain.payment.PgTransactionStatus
import com.loopers.domain.product.Price
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.ZonedDateTime

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
 *
 * 최종 결과는 콜백과 스케줄러가 같은 sync 로 확정한다. 콜백 본문의 status 를 믿지 않는다 — 콜백은 인증이 없어
 * 누구든 SUCCESS 를 보내 결제 없이 주문을 PAID 로 만들 수 있다. 콜백은 "지금 PG 에 물어보라" 는 신호일 뿐이다. (설계 5.1 장)
 *
 * 사람이 PG 를 확인해야 하는 사건은 ManualReviewRecorder 로 센다. 결제를 FAILED 로 닫을 때 세고 발견할 때 세지 않는다 —
 * 발견 시점의 로그는 1 분이 지나기 전까지 동기화 주기마다 반복돼 한 결제가 여러 번 세진다. 이유는 호출 자리로 정한다.
 * 같은 사유 문자열(REASON_AMBIGUOUS)로 닫아도 모호한 거래와 키 소유 충돌이 다른 사건이라 문자열로 가르면 섞인다.
 * (2026-10-30 결제 경보 설계 2 장)
 */
@Component
class PaymentFacade(
    private val userService: UserService,
    private val orderService: OrderService,
    private val paymentService: PaymentService,
    private val paymentGateway: PaymentGateway,
    private val transactionTemplate: TransactionTemplate,
    private val manualReviewRecorder: ManualReviewRecorder,
) {
    companion object {
        private const val REASON_NOT_SENT = "결제 시스템이 불안정합니다. 잠시 후 다시 시도해주세요."
        private const val REASON_NOT_REACHED = "결제 요청이 PG 에 도달하지 않았습니다. 다시 시도해주세요."
        private const val REASON_PG_FAILED = "결제에 실패했습니다."
        private const val REASON_AMBIGUOUS = "PG 거래를 특정할 수 없습니다. 고객센터에 문의해주세요."
        private const val REASON_AMOUNT_MISMATCH = "PG 결제 금액이 주문 금액과 다릅니다."
        private const val REASON_ORDER_MISMATCH = "PG 거래의 주문이 결제 주문과 다릅니다."

        /** PG 처리 지연이 최대 5 초다. 정상이면 콜백이 먼저 처리했을 건만 남도록 넉넉히 둔다. (설계 5.3 장) */
        private val SYNC_DELAY: Duration = Duration.ofSeconds(30)
        private const val SYNC_BATCH_SIZE = 100

        /**
         * 조회 순간에 요청이 아직 네트워크 위에 있거나 PG 가 저장 중일 수 있다. 바로 FAILED 로 확정하면
         * 사용자가 재결제한 뒤 원래 요청까지 승인돼 이중 결제가 난다. (설계 5.2 장)
         */
        private val GIVE_UP_AFTER: Duration = Duration.ofMinutes(1)

        /**
         * 요청이 아직 PG 로 가는 중일 수 있는 시간. 요청의 최대 점유 시간 5.1 s(설계 6.1 장)에 여유를 둔다.
         * tx2 완료를 기준으로 삼지 않는 이유 — 읽기 타임아웃으로 끝난 요청도 PG 쪽에서는 계속 처리된다.
         * 이 안에 모르는 키(고아 거래일 수 있다)를 연결하면, 뒤이어 이번 요청의 거래까지 승인돼 이중 결제가 된다. (설계 5.2 · 9.5 장)
         */
        private val IN_FLIGHT: Duration = Duration.ofSeconds(6)
    }

    private val log = LoggerFactory.getLogger(PaymentFacade::class.java)

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
        } catch (e: DataIntegrityViolationException) {
            // applyRequestResult 가 키 소유를 확인했는데도 유일 제약에 걸렸다면, 확인과 커밋 사이에 다른 요청이 같은 키를 먼저 커밋한 것이다.
            // PG 는 이미 접수했으므로 500 으로 돌려주지 않고 현재 상태(REQUESTED)를 돌려준다. 다음 동기화가 키 소유 확인으로 닫는다.
            // 여기서만 잡는다 — 다른 경로에서 일반적으로 삼키면 무관한 무결성 버그를 가린다.
            // (설계 5.2 장, 2026-10-22 측정이 드러낸 키 충돌, 11.4 장)
            log.error("[paymentId = {}] 결제 요청 결과의 거래 키가 다른 결제와 겹쳐 기록하지 못했습니다. 동기화가 닫습니다.", payment.id, e)
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
            throw CoreException(ErrorType.CONFLICT, "[orderId = ${order.id}] 결제할 수 없는 상태의 주문입니다.")
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

    /**
     * tx2. PG 호출 결과를 반영한다. 즉시 실패로 확정하는 것은 NotSent 와, 키가 다른 결제의 것인 Accepted 뿐이다. (설계 4.2 장)
     * 키 소유 확인이 markPending 보다 앞이다 — 커밋에서 유일 제약에 걸리면 PG 가 접수한 요청이 500 이 된다.
     */
    private fun applyRequestResult(paymentId: Long, result: PgRequestResult): PaymentInfo {
        val payment = paymentService.getPayment(paymentId)!!
        when (result) {
            is PgRequestResult.Accepted -> {
                if (!closeIfKeyOwnedByOther(payment, result.transactionKey)) {
                    payment.markPending(result.transactionKey)
                }
            }
            PgRequestResult.NotSent -> {
                payment.fail(REASON_NOT_SENT)
                orderService.failPayment(payment.orderId)
            }
            PgRequestResult.Unknown -> Unit
        }
        return PaymentInfo.of(payment)
    }

    /**
     * PG 콜백. 본문에서 쓰는 것은 결제 건을 찾을 키와 주문 ID 뿐이다.
     * 키로 못 찾으면 주문의 진행 중 결제로 찾는다 — 키를 기록하기 전에 콜백이 온 경우다.
     * 단, 그 결제에 이미 다른 키가 있으면 이 콜백은 그 결제의 것이 아니다(고아 거래 또는 위조). 건드리지 않는다. (설계 5.1 장)
     */
    fun handleCallback(transactionKey: String, pgOrderId: String, now: ZonedDateTime = ZonedDateTime.now()) {
        paymentService.findByTransactionKey(transactionKey)?.let {
            sync(it.id, now)
            return
        }

        val unlinked = pgOrderId.toLongOrNull()
            ?.let { paymentService.findInProgressByOrderId(it) }
            ?.takeIf { it.transactionKey == null }
        if (unlinked == null) {
            // 1 분 뒤 FAILED 로 확정한 요청이 뒤늦게 승인된 고아 거래일 수 있다. 사람이 PG 를 조회해 확인한다. (설계 9.1 장)
            log.error("[transactionKey = {}, orderId = {}] 콜백과 이어지는 결제가 없습니다.", transactionKey, pgOrderId)
            // 바꾸는 상태가 없어 트랜잭션이 없으므로 기록기가 즉시 센다. (2026-10-30 결제 경보 설계 2 · 3 장)
            manualReviewRecorder.record(ManualReviewReason.UNLINKED_CALLBACK)
            return
        }
        // 생성 후 6 s 안이면 sync 가 아무것도 하지 않는다. 이 콜백은 놓치고 스케줄러가 30 s 뒤에 확정한다. (설계 5.2 장)
        sync(unlinked.id, now)
    }

    /** 스케줄러가 부른다. 한 건의 실패가 나머지를 막지 않게 건 단위로 잡는다. 처리한 건수를 돌려준다. */
    fun syncPending(now: ZonedDateTime = ZonedDateTime.now()): Int {
        val targets = paymentService.findSyncTargets(updatedBefore = now.minus(SYNC_DELAY), limit = SYNC_BATCH_SIZE)
        targets.forEach { payment ->
            runCatching { sync(payment.id, now) }
                .onFailure { log.error("[paymentId = {}] 결제 동기화에 실패했습니다.", payment.id, it) }
        }
        return targets.size
    }

    /**
     * PG 에 물어 결제를 확정한다. 콜백과 스케줄러가 함께 쓰는 유일한 확정 경로다. (설계 5.2 장)
     *
     * PG 조회는 트랜잭션 밖에서 한다. 요청과 같은 이유다 — 조회가 느려도 커넥션을 쥐지 않는다. (설계 4.1 장)
     * 조회가 실패하면(Unavailable) 아무것도 바꾸지 않는다. "모른다" 를 실패로 확정하지 않는다.
     */
    fun sync(paymentId: Long, now: ZonedDateTime = ZonedDateTime.now()) {
        val payment = paymentService.getPayment(paymentId) ?: return
        if (payment.status.isFinal) return
        // 키가 없는 결제는 요청이 아직 가는 중일 수 있다. 이때 PG 목록의 모르는 키를 연결하면 이중 결제가 날 수 있다. (설계 5.2 장)
        if (payment.transactionKey == null && payment.createdAt.isAfter(now.minus(IN_FLIGHT))) return

        val transactionKey = payment.transactionKey ?: when (val found = findUnlinkedTransaction(payment)) {
            is Unlinked.Found -> found.transactionKey
            Unlinked.None -> {
                giveUpIfExpired(payment.id, payment.createdAt, now, REASON_NOT_REACHED, reviewReason = null)
                return
            }
            Unlinked.Ambiguous -> {
                giveUpIfExpired(payment.id, payment.createdAt, now, REASON_AMBIGUOUS, ManualReviewReason.AMBIGUOUS_TRANSACTION)
                return
            }
            Unlinked.Unavailable -> return
        }

        val detail = paymentGateway.findTransaction(payment.userId, transactionKey)
        if (detail !is PgQueryResult.Found) return
        inTransaction(payment.id) { confirm(payment.id, detail.value) }
    }

    /**
     * 운영자가 스케줄러를 기다리지 않고 특정 결제를 PG 에 다시 묻는다. 콜백이 오지 않았거나 급히 확정해야 할 때 쓴다.
     *
     * 확정은 sync 를 그대로 부른다 — 6 s 유예 · 1 분 포기 · 금액/주문 불일치 · 키 소유 확인을 우회하거나 복제하지 않는다.
     * 그래서 요청된 지 얼마 안 된 건은 운영자가 눌러도 아직 아무것도 안 바뀐다. 응답의 status 로 판단한다.
     * 이미 최종 상태면 sync 가 아무것도 하지 않으므로 그 상태를 그대로 돌려준다. 최종 상태는 재오픈하지 않는다.
     * (고아 거래는 설계 9.1 장대로 사람이 처리한다. 2026-10-26 결제 설계 5.5 장)
     *
     * sync 는 없는 결제에 조용히 return 하므로 존재 확인은 여기서 한다. PG 조회 실패(Unavailable)도 예외가 아니라
     * 현재 상태(REQUESTED · PENDING)로 응답한다 — "모른다" 를 오류로 만들지 않는다.
     */
    fun syncManually(paymentId: Long, now: ZonedDateTime = ZonedDateTime.now()): PaymentInfo {
        if (paymentService.getPayment(paymentId) == null) {
            throw CoreException(ErrorType.NOT_FOUND, "[paymentId = $paymentId] 존재하지 않는 결제입니다.")
        }
        sync(paymentId, now)
        return PaymentInfo.of(paymentService.getPayment(paymentId)!!)
    }

    /** 요청됨 결제의 PG 거래를 찾은 결과. Ambiguous 는 "모르는 키가 둘 이상" 이다. (설계 5.2 장) */
    private sealed interface Unlinked {
        data class Found(val transactionKey: String) : Unlinked

        data object None : Unlinked

        data object Ambiguous : Unlinked

        data object Unavailable : Unlinked
    }

    /**
     * 결제를 바꾸는 트랜잭션. 다른 경로(콜백 · 스케줄러 · 결제 요청의 tx2)가 같은 결제를 먼저 바꿨으면 @Version 검사로
     * 이 시도가 롤백되는데, 그건 정상 경합이다. 버리고 넘어간다 — 아직 확정 전이면 다음 동기화가 PG 에 다시 묻는다.
     * 예외를 올리면 콜백이 500 이 되고 스케줄러는 매번 ERROR 를 남긴다. (2026-10-13 결제 설계 5.2 장)
     */
    private fun inTransaction(paymentId: Long, block: () -> Unit) {
        try {
            transactionTemplate.executeWithoutResult { block() }
        } catch (e: OptimisticLockingFailureException) {
            log.info("[paymentId = {}] 다른 경로가 결제를 먼저 바꿔 이번 확정을 건너뜁니다.", paymentId)
        }
    }

    /**
     * 요청됨 결제의 거래를 PG 에서 찾는다. 우리가 모르는 키가 이번 요청의 거래다 — 과거 실패 결제의 키는 이미 연결돼 있고,
     * 진행 중 결제가 주문당 1 건이라 정상이면 모르는 키는 많아야 하나다. (설계 5.2 장)
     *
     * 둘 이상이면 1 분 뒤 포기한 결제가 뒤늦게 승인된 고아 거래가 섞인 것이다. PG 목록에는 금액도 시각도 없어 어느 쪽이 이번 요청인지
     * 가릴 수 없다. firstOrNull 로 하나를 고르면 고아의 결과로 이번 결제를 확정하게 되므로 연결하지 않는다. (설계 9.5 장)
     */
    private fun findUnlinkedTransaction(payment: PaymentModel): Unlinked =
        when (val result = paymentGateway.findTransactionsByOrder(payment.userId, payment.orderId)) {
            is PgQueryResult.Found -> {
                val known = paymentService.findAllByOrderId(payment.orderId).mapNotNull { it.transactionKey }.toSet()
                val unknown = result.value.map { it.transactionKey }.filter { it !in known }.distinct()
                when (unknown.size) {
                    0 -> Unlinked.None
                    1 -> Unlinked.Found(unknown.single())
                    else -> {
                        log.error("[paymentId = {}] 모르는 PG 거래가 둘 이상이라 연결하지 않습니다. keys = {}", payment.id, unknown)
                        Unlinked.Ambiguous
                    }
                }
            }
            PgQueryResult.NotFound -> Unlinked.None
            PgQueryResult.Unavailable -> Unlinked.Unavailable
        }

    /**
     * @param reviewReason 실제로 포기(fail)했을 때만 기록할 이유. REASON_NOT_REACHED 는 null — 세지 않는다.
     *   뒤늦게 승인되면 콜백이 unlinked_callback 으로 센다. (2026-10-30 결제 경보 설계 2 장)
     */
    private fun giveUpIfExpired(
        paymentId: Long,
        createdAt: ZonedDateTime,
        now: ZonedDateTime,
        reason: String,
        reviewReason: ManualReviewReason?,
    ) {
        if (createdAt.isAfter(now.minus(GIVE_UP_AFTER))) return
        inTransaction(paymentId) {
            val payment = paymentService.getPayment(paymentId)!!
            // 조회와 이 트랜잭션 사이에 콜백이 키를 연결했을 수 있다. 요청됨일 때만 포기한다.
            // 이 확인과 커밋 사이에 끼어든 확정은 @Version 이 막는다.
            if (payment.status != PaymentStatus.REQUESTED) return@inTransaction
            payment.fail(reason)
            orderService.failPayment(payment.orderId)
            reviewReason?.let { manualReviewRecorder.record(it) }
        }
    }

    /**
     * 결제와 주문을 한 트랜잭션에서 확정한다. 다른 경로가 먼저 확정했으면 결제 전이는 무시되고 주문 UPDATE 는 0 행이다.
     *
     * 결제 전이를 주문 UPDATE 보다 먼저 한다 — 조건부 UPDATE 의 flush 가 결제 변경을 먼저 내보내고 컨텍스트를 비운다.
     * 버전 검사도 그 flush 에서 일어나므로, 경합에 진 쪽은 주문을 건드리기 전에 실패하고 통째로 롤백된다.
     * 이 순서를 뒤집으면 진 쪽이 주문 UPDATE 를 먼저 실행한다. 롤백되긴 하지만 그동안 주문 행 락을 쥐어 이긴 쪽을 기다리게 한다.
     */
    private fun confirm(paymentId: Long, transaction: PgTransaction) {
        val payment = paymentService.getPayment(paymentId)!!
        if (payment.status.isFinal) return

        // 키를 연결하기 전(요청됨)에만 소유를 확인한다. 이미 키가 있는 결제(대기)는 그 키가 이 결제의 것이다.
        if (payment.transactionKey == null && closeIfKeyOwnedByOther(payment, transaction.transactionKey)) return

        // PG 상세의 주문이 이 결제의 주문과 다르면 PG 가 키를 중복 발급해 다른 주문의 거래가 이 키로 덮어써진 것이다.
        // 그 결과로 확정하면 남의 거래로 이 결제를 확정한다. 금액 불일치와 같은 이유로 반영하지 않고 닫는다.
        // 키는 기록하지 않는다 — 요청됨이면 연결하려던 키가 이 결제의 것이라는 근거가 없고, 대기면 이미 기록돼 있다.
        // (설계 5.2 · 9.5 장, 2026-10-22 측정이 드러낸 키 충돌, 11.4 장)
        if (transaction.orderId != null && transaction.orderId != payment.orderId) {
            log.error(
                "[paymentId = {}, transactionKey = {}] PG 거래의 주문({})이 결제 주문({})과 다릅니다. 실패로 닫습니다.",
                paymentId,
                transaction.transactionKey,
                transaction.orderId,
                payment.orderId,
            )
            payment.fail(REASON_ORDER_MISMATCH)
            orderService.failPayment(payment.orderId)
            manualReviewRecorder.record(ManualReviewReason.ORDER_MISMATCH)
            return
        }

        payment.markPending(transaction.transactionKey)
        if (transaction.amount != payment.amount.value) {
            // 위조나 버그의 신호라 PG 의 결과를 반영하지 않는다. 그대로 두면 상태가 영영 안 바뀌어 매 주기 동기화 대상으로 남고
            // 쌓이면 스케줄러 앞자리를 막으므로 실패로 닫는다. 키는 위에서 기록했다 — 뒤늦은 콜백이 이 결제 건을 찾게.
            // PG 에서 돈이 나갔을 수 있어 후속은 수동이다. (설계 5.2 · 9.5 장)
            log.error(
                "[paymentId = {}, transactionKey = {}] PG 금액({})이 결제 금액({})과 다릅니다. 실패로 닫습니다.",
                paymentId,
                transaction.transactionKey,
                transaction.amount,
                payment.amount.value,
            )
            payment.fail(REASON_AMOUNT_MISMATCH)
            orderService.failPayment(payment.orderId)
            manualReviewRecorder.record(ManualReviewReason.AMOUNT_MISMATCH)
            return
        }
        when (transaction.status) {
            PgTransactionStatus.PENDING -> Unit
            PgTransactionStatus.SUCCESS -> {
                payment.succeed()
                orderService.completePayment(payment.orderId)
            }
            PgTransactionStatus.FAILED -> {
                payment.fail(transaction.reason ?: REASON_PG_FAILED)
                orderService.failPayment(payment.orderId)
            }
        }
    }

    /**
     * 연결하려는 키가 이미 다른 결제의 것이면 키를 기록하지 않고 이 결제를 실패로 닫는다. 닫았으면 true.
     * 유일성은 전역인데 PG 가 준 키를 이 결제의 것이라 가정하면 커밋에서 uk_payments_transaction_key 를 위반해,
     * 스케줄러는 매 주기 ERROR 로 영원히 미확정이 되고 요청은 500 이 된다. 주문이 풀려 재결제할 수 있다.
     * PG 에서 돈이 나갔을 수 있어 후속은 수동이다. (설계 5.2 · 9.1 · 9.5 장, 2026-10-22 측정이 드러낸 키 충돌, 11.4 장)
     *
     * 두 트랜잭션이 동시에 같은 키를 확인하면 둘 다 통과하고 한쪽 커밋이 유일 제약에 걸릴 수 있다. 결제 요청은 request() 가,
     * 동기화는 syncPending 의 건 단위 로그가 받고, 다음 주기의 이 확인이 닫는다.
     */
    private fun closeIfKeyOwnedByOther(payment: PaymentModel, transactionKey: String): Boolean {
        val owner = paymentService.findByTransactionKey(transactionKey)?.takeIf { it.id != payment.id } ?: return false
        log.error(
            "[paymentId = {}, ownerPaymentId = {}, transactionKey = {}] 거래 키가 다른 결제에 연결돼 있어 연결하지 않고 실패로 닫습니다.",
            payment.id,
            owner.id,
            transactionKey,
        )
        // fail 은 이미 최종 상태면 아무것도 바꾸지 않는다. tx2 경로는 isFinal 확인 없이 불리므로 바뀐 것이 있을 때만 센다.
        // (2026-10-30 결제 경보 설계 2 장)
        val wasFinal = payment.status.isFinal
        payment.fail(REASON_AMBIGUOUS)
        orderService.failPayment(payment.orderId)
        if (!wasFinal) manualReviewRecorder.record(ManualReviewReason.KEY_OWNER_CONFLICT)
        return true
    }

    private fun getUserOrThrow(loginId: LoginId): UserModel =
        userService.getUser(loginId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[loginId = ${loginId.value}] 존재하지 않는 회원입니다.")
}
