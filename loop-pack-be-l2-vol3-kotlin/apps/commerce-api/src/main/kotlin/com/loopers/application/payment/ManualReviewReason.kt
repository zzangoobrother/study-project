package com.loopers.application.payment

/**
 * 사람이 PG 를 확인해야 하는 결제 사건의 이유. [tag] 는 `payment.manual.review` 카운터의 `reason` 태그 값이다.
 * (2026-10-30 결제 경보 설계 2 장)
 *
 * 이유는 호출 자리로 정한다. 실패 사유 문자열로 가르면 `ambiguous_transaction` 과 `key_owner_conflict` 가 섞인다.
 */
enum class ManualReviewReason(val tag: String) {
    UNLINKED_CALLBACK("unlinked_callback"),
    AMBIGUOUS_TRANSACTION("ambiguous_transaction"),
    AMOUNT_MISMATCH("amount_mismatch"),
    ORDER_MISMATCH("order_mismatch"),
    KEY_OWNER_CONFLICT("key_owner_conflict"),
}
