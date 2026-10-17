package com.loopers.domain.payment

/**
 * 결제 상태.
 *
 * REQUESTED 는 "PG 에 닿았는지 모른다" 를 저장한 상태다. 타임아웃을 FAILED 로 처리하면 PG 에선 승인됐는데
 * 우리는 실패로 남는다. 모르는 것은 모른다고 적어 두고 PG 에 물어서 확정한다. (2026-10-13 결제 설계 3.1 장)
 */
enum class PaymentStatus {
    REQUESTED,
    PENDING,
    SUCCESS,
    FAILED,
    ;

    val isFinal: Boolean get() = this == SUCCESS || this == FAILED

    companion object {
        /** 진행 중 — 주문당 최대 1 건이고 동기화 대상이다. */
        val IN_PROGRESS: Set<PaymentStatus> = setOf(REQUESTED, PENDING)
    }
}
