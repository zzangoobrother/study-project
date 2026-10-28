package com.loopers.infrastructure.payment

import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * Bulkhead 가 거절한 호출 수를 `pg.bulkhead.rejected{name}` 카운터로 남긴다. Prometheus 이름은 `pg_bulkhead_rejected_total`.
 * (2026-10-29 PG 격리 설계 5 장)
 *
 * 직접 붙이는 이유: resilience4j-micrometer 2.2 의 Bulkhead 지표는 available · max allowed 게이지 둘뿐이라 거절 수가
 * 나오지 않는다. 게이지만으로는 "상한에 닿았다" 는 순간값만 보일 뿐 몇 건이 막혔는지는 알 수 없다.
 * 이 카운터는 부하 측정에서 거절 수를 기록하는 데 쓴다. (설계 8 장)
 *
 * 카운터는 생성 시점에 미리 등록해 거절이 한 번도 없어도 0 으로 보이게 한다.
 */
@Component
class PgBulkheadMetrics(
    bulkheadRegistry: BulkheadRegistry,
    meterRegistry: MeterRegistry,
) {
    init {
        listOf(PG_REQUEST, PG_QUERY).forEach { name ->
            val counter = Counter.builder(REJECTED)
                .description("PG Bulkhead 가 동시 호출 상한 때문에 거절한 호출 수")
                .tag("name", name)
                .register(meterRegistry)
            bulkheadRegistry.bulkhead(name).eventPublisher.onCallRejected { counter.increment() }
        }
    }

    private companion object {
        const val REJECTED = "pg.bulkhead.rejected"
        const val PG_REQUEST = "pgRequest"
        const val PG_QUERY = "pgQuery"
    }
}
