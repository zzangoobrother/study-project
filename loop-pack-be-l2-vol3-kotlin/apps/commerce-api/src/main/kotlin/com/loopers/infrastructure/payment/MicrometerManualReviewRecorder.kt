package com.loopers.infrastructure.payment

import com.loopers.application.payment.ManualReviewReason
import com.loopers.application.payment.ManualReviewRecorder
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * 수동 확인이 필요한 결제를 `payment.manual.review{reason}` 카운터로 센다. Prometheus 이름은 `payment_manual_review_total`.
 *
 * 트랜잭션 안에서 불리면 커밋된 뒤에 올린다. 낙관적 락 충돌로 롤백된 시도는 사건이 없던 일인데, 그 안에서 올리면
 * 경보가 실제보다 크게 울린다. 트랜잭션이 없으면(콜백 경로) 즉시 올린다. (2026-10-30 결제 경보 설계 3 장)
 *
 * 카운터는 생성 시점에 이유별로 미리 등록해 한 번도 없어도 0 으로 보이게 한다. 경보식이 "없음" 과 "0" 을
 * 구분하지 않아도 된다. (2026-10-30 결제 경보 설계 1 장)
 */
@Component
class MicrometerManualReviewRecorder(
    meterRegistry: MeterRegistry,
) : ManualReviewRecorder {
    private val counters: Map<ManualReviewReason, Counter> = ManualReviewReason.entries.associateWith { reason ->
        Counter.builder(NAME)
            .description("사람이 PG 를 확인해야 하는 결제 사건 수")
            .tag("reason", reason.tag)
            .register(meterRegistry)
    }

    override fun record(reason: ManualReviewReason) {
        val counter = counters.getValue(reason)
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        counter.increment()
                    }
                },
            )
        } else {
            counter.increment()
        }
    }

    private companion object {
        const val NAME = "payment.manual.review"
    }
}
