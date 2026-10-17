package com.loopers.domain.payment

import com.loopers.domain.BaseEntity
import com.loopers.domain.product.Price
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version

/**
 * 결제 애그리거트 루트. 주문 하나에 여러 건일 수 있다 — 실패하면 다른 카드로 다시 결제한다. (2026-10-13 결제 설계 3.2 장)
 *
 * 주문과 회원은 식별자로만 참조한다. 결제는 주문의 부품이 아니다 — 주문 없이 생기지는 않지만,
 * 주문과 따로 만들어지고 따로 저장되며 PG 거래 키로 주문을 거치지 않고 조회된다.
 *
 * 전이 메서드가 확정 상태에서 예외 없이 무시하는 이유는 콜백과 스케줄러가 같은 결과를 동시에 들고 오는 것이 정상 경로이기 때문이다.
 * 예외를 던지면 둘 중 하나가 매번 500 이 된다. (설계 3.1 장)
 *
 * 주의 — dev·qa·prd 는 ddl-auto 가 none 이므로 payments 테이블을 스키마에 직접 만들어야 한다(설계 3.1 장의 CREATE TABLE).
 */
@Entity
@Table(
    name = "payments",
    indexes = [
        Index(name = "idx_payments_order_id", columnList = "order_id"),
        Index(name = "idx_payments_status_updated_at", columnList = "status, updated_at"),
    ],
    uniqueConstraints = [UniqueConstraint(name = "uk_payments_transaction_key", columnNames = ["transaction_key"])],
)
class PaymentModel private constructor(
    orderId: Long,
    userId: Long,
    amount: Price,
    cardType: CardType,
    cardNo: CardNo,
) : BaseEntity() {
    @Column(name = "order_id", nullable = false)
    var orderId: Long = orderId
        protected set

    /** PG 의 X-USER-ID 로도 쓴다. PG 는 조회 때 요청 때와 같은 값을 요구하므로 저장해 둔 이 값을 계속 쓴다. */
    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    /** 결제 요청 시점의 order.paidAmount 스냅샷. */
    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "amount", nullable = false))
    var amount: Price = amount
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 20)
    var cardType: CardType = cardType
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "card_no", nullable = false))
    var cardNo: CardNo = cardNo
        protected set

    /** PG 가 접수하기 전에는 null 이다. MySQL 유니크 인덱스는 NULL 을 여러 개 허용한다. */
    @Column(name = "transaction_key")
    var transactionKey: String? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: PaymentStatus = PaymentStatus.REQUESTED
        protected set

    @Column(name = "failure_reason")
    var failureReason: String? = null
        protected set

    /**
     * 낙관적 락. 주문 전이는 조건부 UPDATE 로 직렬화되지만 결제 전이는 읽고 바꾸는 더티 체킹이라,
     * 버전이 없으면 동시에 REQUESTED 를 읽은 두 확정 경로가 각자 커밋해 "결제 FAILED · 주문 PAID" 로 어긋난 채 굳을 수 있다.
     * 전이 메서드가 확정 상태를 무시하는 것(멱등)은 순차 실행에서만 통한다. (2026-10-13 결제 설계 3.1 장)
     *
     * BaseEntity 가 아니라 여기 두는 이유 — BaseEntity 는 세 앱이 공유한다.
     */
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
        protected set

    init {
        if (orderId <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "주문 ID 는 양수여야 합니다.")
        }
        if (userId <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "회원 ID 는 양수여야 합니다.")
        }
    }

    /** PG 가 접수했다. 요청됨이 아니면 무시한다 — 이미 키가 있거나 확정됐다. */
    fun markPending(transactionKey: String) {
        if (status != PaymentStatus.REQUESTED) return
        this.transactionKey = transactionKey
        status = PaymentStatus.PENDING
    }

    fun succeed() {
        if (status.isFinal) return
        status = PaymentStatus.SUCCESS
    }

    fun fail(reason: String) {
        if (status.isFinal) return
        status = PaymentStatus.FAILED
        failureReason = reason
    }

    companion object {
        fun create(
            orderId: Long,
            userId: Long,
            amount: Price,
            cardType: CardType,
            cardNo: CardNo,
        ): PaymentModel = PaymentModel(
            orderId = orderId,
            userId = userId,
            amount = amount,
            cardType = cardType,
            cardNo = cardNo,
        )
    }
}
