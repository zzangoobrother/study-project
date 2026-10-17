package com.loopers.domain.payment

/** PG 조회 결과. Unavailable 은 "모른다" 이므로 호출자는 아무것도 바꾸지 않는다. (2026-10-13 결제 설계 5.2 장) */
sealed interface PgQueryResult<out T> {
    data class Found<T>(val value: T) : PgQueryResult<T>

    data object NotFound : PgQueryResult<Nothing>

    data object Unavailable : PgQueryResult<Nothing>
}
