package com.loopers.interfaces.scheduler

import com.loopers.application.payment.PaymentFacade
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 콜백이 오지 않은 결제를 PG 에 물어 확정한다.
 *
 * interfaces 에 두는 이유 — 시간이라는 외부 자극을 받아 application 을 부르는 입구라는 점에서 컨트롤러와 같다.
 * commerce-batch 에 두지 않는 이유 — PG 클라이언트와 도메인 클래스가 필요한데 배치 앱은 commerce-api 를 의존하지 않는다.
 * 분산 락을 두지 않는다. 여러 인스턴스가 같은 건을 처리해도 주문 전이는 조건부 UPDATE, 결제 전이는 @Version 으로 직렬화돼
 * 늦은 쪽은 아무 일도 하지 않는다. 전이 메서드의 멱등만으로는 부족하다 — 동시에 읽은 두 트랜잭션은 막지 못한다. (2026-10-13 결제 설계 5.3 장)
 */
@Component
class PaymentSyncScheduler(
    private val paymentFacade: PaymentFacade,
) {
    @Scheduled(fixedDelay = 10_000)
    fun syncPending() {
        paymentFacade.syncPending()
    }
}
