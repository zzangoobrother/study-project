package com.loopers.application.payment

/**
 * 사람이 PG 를 확인해야 하는 결제 사건을 기록한다.
 *
 * 인터페이스를 application 에 두는 이유: `PaymentFacade` 가 Micrometer 를 직접 알지 않게 하려는 것이다.
 * 경보는 도메인 규칙이 아니라 유스케이스의 운영 장치라 domain 에 두지 않는다. 캐시가 인터페이스를 application 에,
 * 구현을 infrastructure 에 둔 것과 같은 배치다. (2026-10-30 결제 경보 설계 4 장)
 */
interface ManualReviewRecorder {
    fun record(reason: ManualReviewReason)
}
