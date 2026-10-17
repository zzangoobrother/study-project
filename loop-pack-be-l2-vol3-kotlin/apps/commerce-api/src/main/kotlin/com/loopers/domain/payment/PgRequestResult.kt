package com.loopers.domain.payment

/** 결제 요청 결과. (2026-10-13 결제 설계 4.2 장) */
sealed interface PgRequestResult {
    /** PG 가 접수했다. 결과는 나중에 온다. */
    data class Accepted(val transactionKey: String) : PgRequestResult

    /** 보내지 않은 것이 확실하다 — 서킷 OPEN, 또는 첫 시도의 연결 거부. 즉시 실패로 확정해도 되는 유일한 경우다. */
    data object NotSent : PgRequestResult

    /** 보냈지만 PG 가 무엇을 남겼는지 모른다. 동기화가 확정한다. */
    data object Unknown : PgRequestResult
}
