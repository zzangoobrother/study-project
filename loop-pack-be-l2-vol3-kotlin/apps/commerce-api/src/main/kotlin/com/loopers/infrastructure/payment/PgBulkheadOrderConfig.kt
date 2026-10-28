package com.loopers.infrastructure.payment

import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry
import io.github.resilience4j.spring6.bulkhead.configure.BulkheadAspect
import io.github.resilience4j.spring6.bulkhead.configure.BulkheadAspectExt
import io.github.resilience4j.spring6.bulkhead.configure.BulkheadConfigurationProperties
import io.github.resilience4j.spring6.fallback.FallbackExecutor
import io.github.resilience4j.spring6.spelresolver.SpelResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Bulkhead 를 가장 바깥으로 보낸다. Bulkhead( CircuitBreaker( Retry( 호출 ) ) ) (2026-10-29 PG 격리 설계 2 장)
 *
 * 서킷 · 재시도와 달리 Bulkhead 의 aspect-order 는 yml 키가 없다. resilience4j-spring6 2.2.0 의
 * BulkheadConfigurationProperties.getBulkheadAspectOrder 가 Int.MAX_VALUE - 1(가장 안쪽)로 고정이고 setter 가 없어,
 * resilience4j.bulkhead.bulkhead-aspect-order 를 쓰면 바인딩 실패로 컨텍스트가 뜨지 않는다. 그래서 자동 구성의
 * BulkheadAspect 를 같은 구성으로 직접 만들되 순서만 0 으로 한다(자동 구성은 이 빈이 있으면 물러난다).
 * 라이브러리를 올릴 때 이 순서가 유지되는지는 PgPaymentGatewayBulkheadTest 의 "통과한 요청 3 회씩 시도" 단언이 잡는다.
 */
@Configuration
class PgBulkheadOrderConfig {
    @Bean
    fun bulkheadAspect(
        properties: BulkheadConfigurationProperties,
        threadPoolBulkheadRegistry: ThreadPoolBulkheadRegistry,
        bulkheadRegistry: BulkheadRegistry,
        aspectExts: List<BulkheadAspectExt>,
        fallbackExecutor: FallbackExecutor,
        spelResolver: SpelResolver,
    ): BulkheadAspect = object : BulkheadAspect(
        properties,
        threadPoolBulkheadRegistry,
        bulkheadRegistry,
        aspectExts,
        fallbackExecutor,
        spelResolver,
    ) {
        override fun getOrder(): Int = OUTERMOST
    }

    private companion object {
        // 값이 작을수록 바깥이다. 서킷 1 · 재시도 2 보다 작다.
        const val OUTERMOST = 0
    }
}
