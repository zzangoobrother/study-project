package com.loopers.infrastructure.payment

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.reset
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.loopers.domain.payment.CardNo
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentGateway
import com.loopers.domain.payment.PgPaymentRequest
import com.loopers.domain.payment.PgQueryResult
import com.loopers.domain.payment.PgRequestResult
import com.loopers.domain.product.Price
import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Bulkhead 의 거절 · 순서 · 허가 반환을 증명하는 테스트다. (2026-10-29 PG 격리 설계 2 · 4 · 5 · 6 장)
 *
 * 상한을 2 로 덮어쓴다 — 운영 값 50 을 채우는 테스트는 무겁다. 운영 값(50 · 20 · 대기 0)은 덮어쓰기 없는
 * 컨텍스트를 쓰는 PgBulkheadConfigTest 가 지킨다. 재시도 대기는 10 ms 로 줄이며, 겹침이 흔들리지 않게
 * 스텁에 고정 지연을 둔다.
 */
@SpringBootTest(
    properties = [
        "pg.base-url=http://localhost:\${wiremock.server.port}",
        "resilience4j.retry.instances.pgRequest.wait-duration=10ms",
        "resilience4j.bulkhead.instances.pgRequest.max-concurrent-calls=2",
        "resilience4j.bulkhead.instances.pgQuery.max-concurrent-calls=2",
    ],
)
@AutoConfigureWireMock(port = 0)
class PgPaymentGatewayBulkheadTest @Autowired constructor(
    private val paymentGateway: PaymentGateway,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val bulkheadRegistry: BulkheadRegistry,
    private val meterRegistry: MeterRegistry,
) {
    companion object {
        private const val PAYMENTS = "/api/v1/payments"
        private const val KEY = "20251013:TR:abc123"
        private const val DELAY_MS = 400
    }

    @BeforeEach
    fun setUp() {
        reset()
        circuitBreakerRegistry.allCircuitBreakers.forEach { it.reset() }
    }

    private fun request(orderId: Long = 42L) = paymentGateway.request(
        PgPaymentRequest(
            userId = 7L,
            orderId = orderId,
            cardType = CardType.SAMSUNG,
            cardNo = CardNo("1234-5678-9814-1451"),
            amount = Price(5_000),
        ),
    )

    private fun accepted(): ResponseDefinitionBuilder = okJson(
        """{"meta":{"result":"SUCCESS","errorCode":null,"message":null},
           "data":{"transactionKey":"$KEY","status":"PENDING","reason":null}}""",
    )

    private fun failure(status: Int): ResponseDefinitionBuilder = aResponse()
        .withStatus(status)
        .withHeader("Content-Type", "application/json")
        .withBody("""{"meta":{"result":"FAIL","errorCode":"ERROR","message":"실패"},"data":null}""")

    private fun stubRequest(response: ResponseDefinitionBuilder) {
        stubFor(post(urlPathEqualTo(PAYMENTS)).willReturn(response))
    }

    /** 같은 순간에 출발시켜 결과를 모은다. 한 요청이 끝나기 전에 모두 허가를 시도하도록 스텁 지연이 겹침을 만든다. */
    private fun <T> concurrently(count: Int, task: () -> T): List<T> {
        val executor = Executors.newFixedThreadPool(count)
        try {
            val ready = CountDownLatch(count)
            val start = CountDownLatch(1)
            val futures = (1..count).map {
                executor.submit<T> {
                    ready.countDown()
                    start.await()
                    task()
                }
            }
            ready.await(5, TimeUnit.SECONDS)
            start.countDown()
            return futures.map { it.get(20, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    /** 카운터를 만들어 주지 않고 찾기만 한다 — 구현이 미리 등록하지 않았다면 여기서 실패해야 한다. */
    private fun rejected(name: String): Double =
        checkNotNull(meterRegistry.find("pg.bulkhead.rejected").tag("name", name).counter()) { "카운터 없음: $name" }.count()

    private fun <T> List<T>.countOf(predicate: (T) -> Boolean) = count(predicate)

    @DisplayName("결제 요청이 상한을 넘으면, ")
    @Nested
    inner class RequestOverflow {
        @DisplayName("넘친 요청은 PG 로 보내지 않고 NotSent 다.")
        @Test
        fun returnsNotSent_forOverflow() {
            // arrange
            stubRequest(accepted().withFixedDelay(DELAY_MS))

            // act
            val results = concurrently(3) { request() }

            // assert
            assertAll(
                { assertThat(results.countOf { it == PgRequestResult.NotSent }).isEqualTo(1) },
                { assertThat(results.countOf { it is PgRequestResult.Accepted }).isEqualTo(2) },
                { verify(2, postRequestedFor(urlPathEqualTo(PAYMENTS))) },
            )
        }

        /**
         * 검토 초점 1. 거절된 요청은 PG 에 0 회, 통과한 요청은 재시도를 끝까지 한다(2 개 x 3 회 = 6). 이 테스트는 순서를 증명하지 않는다 —
         * 순서가 뒤집혀도 이 시나리오는 같은 값이 나온다. 순서는 PgBulkheadConfigTest 의 빈 순서 단언과
         * rejectionIsNotRecordedByCircuit 의 서킷 버퍼 2 가 지킨다. (설계 2 장)
         */
        @DisplayName("거절은 첫 시도 전에만 일어난다 — 통과한 요청은 3 회씩 시도하고 Unknown 이다.")
        @Test
        fun rejectsOnlyBeforeFirstAttempt() {
            // arrange
            stubRequest(failure(500).withFixedDelay(DELAY_MS))

            // act
            val results = concurrently(3) { request() }

            // assert
            assertAll(
                { assertThat(results.countOf { it == PgRequestResult.NotSent }).isEqualTo(1) },
                { assertThat(results.countOf { it == PgRequestResult.Unknown }).isEqualTo(2) },
                { verify(6, postRequestedFor(urlPathEqualTo(PAYMENTS))) },
            )
        }

        /** 검토 초점 3. 거절이 서킷 안으로 들어가면 우리가 막은 것이 실패율을 올려 서킷까지 연다. (설계 1 장 반목표 3) */
        @DisplayName("거절은 서킷의 실패 · 호출 수로 세지 않는다.")
        @Test
        fun rejectionIsNotRecordedByCircuit() {
            // arrange
            stubRequest(failure(500).withFixedDelay(DELAY_MS))

            // act
            concurrently(3) { request() }

            // assert
            val metrics = circuitBreakerRegistry.circuitBreaker("pgRequest").metrics
            assertAll(
                { assertThat(metrics.numberOfFailedCalls).isEqualTo(2) },
                { assertThat(metrics.numberOfBufferedCalls).isEqualTo(2) },
                { assertThat(metrics.numberOfNotPermittedCalls).isEqualTo(0) },
            )
        }

        /** 검토 초점 2. 타임아웃 · 4xx · 재시도 소진 경로에서 허가가 새면 상한이 조용히 줄어든다. */
        @DisplayName("실패한 요청도 허가를 돌려준다 — 타임아웃 · 4xx · 5xx 소진 뒤에도 상한만큼 통과한다.")
        @Test
        fun releasesPermit_afterFailures() {
            // arrange — 읽기 타임아웃 2 개
            stubRequest(accepted().withFixedDelay(1_500))
            repeat(2) { request() }
            // 4xx 2 개
            reset()
            stubRequest(failure(400))
            repeat(2) { request() }
            // 5xx 소진 2 개
            reset()
            stubRequest(failure(500))
            repeat(2) { request() }
            reset()
            stubRequest(accepted().withFixedDelay(DELAY_MS))

            // act
            val results = concurrently(2) { request() }

            // assert
            assertAll(
                { assertThat(results.countOf { it is PgRequestResult.Accepted }).isEqualTo(2) },
                { assertThat(results.countOf { it == PgRequestResult.NotSent }).isEqualTo(0) },
                { assertThat(bulkheadRegistry.bulkhead("pgRequest").metrics.availableConcurrentCalls).isEqualTo(2) },
            )
        }
    }

    @DisplayName("거절 지표는, ")
    @Nested
    inner class RejectionMetric {
        @DisplayName("거절이 한 번도 없어도 두 카운터가 미리 등록되어 있다.")
        @Test
        fun exposesCounterBeforeAnyRejection() {
            assertAll(
                { assertThat(meterRegistry.find("pg.bulkhead.rejected").tag("name", "pgRequest").counter()).isNotNull() },
                { assertThat(meterRegistry.find("pg.bulkhead.rejected").tag("name", "pgQuery").counter()).isNotNull() },
            )
        }

        /** 거절 수 지표는 라이브러리가 주지 않아 직접 붙인다. 컨텍스트가 캐시로 공유되므로 증가분으로 단언한다. (2026-10-29 PG 격리 설계 5 장) */
        @DisplayName("거절 한 건마다 pg.bulkhead.rejected{name=pgRequest} 가 정확히 1 늘고, pgQuery 는 그대로다.")
        @Test
        fun countsRejection() {
            // arrange
            val requestBefore = rejected("pgRequest")
            val queryBefore = rejected("pgQuery")
            stubRequest(accepted().withFixedDelay(DELAY_MS))

            // act
            concurrently(3) { request() }

            // assert
            assertAll(
                { assertThat(rejected("pgRequest") - requestBefore).isEqualTo(1.0) },
                { assertThat(rejected("pgQuery") - queryBefore).isEqualTo(0.0) },
            )
        }
    }

    @DisplayName("조회가 상한을 넘으면, ")
    @Nested
    inner class QueryOverflow {
        @DisplayName("넘친 조회는 Unavailable 이고 나머지는 Found 다.")
        @Test
        fun returnsUnavailable_whenQueryBulkheadIsFull() {
            // arrange
            stubFor(
                get(urlPathMatching("$PAYMENTS/.+")).willReturn(
                    okJson(
                        """{"meta":{"result":"SUCCESS","errorCode":null,"message":null},
                           "data":{"transactionKey":"$KEY","orderId":"000042","cardType":"SAMSUNG",
                                   "cardNo":"1234-5678-9814-1451","amount":5000,"status":"PENDING","reason":null}}""",
                    ).withFixedDelay(DELAY_MS),
                ),
            )

            // act
            val results = concurrently(3) { paymentGateway.findTransaction(userId = 7L, transactionKey = KEY) }

            // assert
            assertAll(
                { assertThat(results.countOf { it == PgQueryResult.Unavailable }).isEqualTo(1) },
                { assertThat(results.countOf { it is PgQueryResult.Found }).isEqualTo(2) },
            )
        }
    }
}
