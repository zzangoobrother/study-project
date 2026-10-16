package com.loopers.domain.order

/**
 * 주문의 결제 상태.
 *
 * CouponStatus 와 달리 컬럼으로 저장한다. 전이를 일으키는 것이 시간이 아니라 결제 요청 · PG 결과라는 외부 사건이기 때문이다.
 *
 * PAYING 은 "진행 중인 결제가 있다" 를 WHERE 절로 막기 위해 존재한다. 이 상태가 없으면 동시에 들어온 두 결제 요청을
 * 조건부 UPDATE 로 가를 방법이 없다. (2026-10-13 결제 설계 3.3 장)
 *
 * PAYMENT_FAILED 에서 다시 PAYING 으로 갈 수 있다 — PG 의 실패 사유가 "다른 카드를 선택해주세요" 다. (설계 3.2 장)
 */
enum class OrderStatus {
    PAYMENT_PENDING,
    PAYING,
    PAID,
    PAYMENT_FAILED,
}
