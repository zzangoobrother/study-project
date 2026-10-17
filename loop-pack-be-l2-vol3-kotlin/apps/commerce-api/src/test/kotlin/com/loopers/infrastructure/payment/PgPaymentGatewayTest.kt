package com.loopers.infrastructure.payment

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.reset
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.stubbing.Scenario
import com.loopers.domain.payment.CardNo
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentGateway
import com.loopers.domain.payment.PgPaymentRequest
import com.loopers.domain.payment.PgQueryResult
import com.loopers.domain.payment.PgRequestResult
import com.loopers.domain.payment.PgTransaction
import com.loopers.domain.payment.PgTransactionStatus
import com.loopers.domain.product.Price
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock

/**
 * 설정 수치가 실제로 적용됐는지를 증명하는 테스트다. 결과보다 호출 횟수와 시간을 단언한다 —
 * yml 키 하나가 틀리면 기본값으로 돌아가면서도 아무 에러가 나지 않는다. (2026-10-13 결제 설계 10 장)
 *
 * 재시도 대기만 10 ms 로 줄인다. 서킷을 여는 데 실패한 요청 10 건(시도 30 번)이 필요해, 실제 대기(200 → 400 ms)면 6 초가 넘게 걸린다.
 * 횟수 · 예외 분류 · 서킷 수치 · 감싸는 순서는 운영 설정 그대로다.
 */
@SpringBootTest(
    properties = [
        "pg.base-url=http://localhost:\${wiremock.server.port}",
        "resilience4j.retry.instances.pgRequest.wait-duration=10ms",
    ],
)
@AutoConfigureWireMock(port = 0)
class PgPaymentGatewayTest @Autowired constructor(
    private val paymentGateway: PaymentGateway,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
) {
    companion object {
        private const val PAYMENTS = "/api/v1/payments"
        private const val KEY = "20251013:TR:abc123"
    }

    @BeforeEach
    fun setUp() {
        reset()
        // 앞 테스트가 연 서킷이 다음 테스트로 새지 않게 한다.
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

    private fun requestCount(count: Int) = verify(count, postRequestedFor(urlPathEqualTo(PAYMENTS)))

    private fun circuit(name: String): CircuitBreaker = circuitBreakerRegistry.circuitBreaker(name)

    @DisplayName("결제를 요청할 때, ")
    @Nested
    inner class Request {
        @DisplayName("PG 가 접수하면 거래 키와 함께 Accepted 다.")
        @Test
        fun returnsAccepted_whenPgAccepts() {
            // arrange
            stubRequest(accepted())

            // act
            val result = request()

            // assert
            assertThat(result).isEqualTo(PgRequestResult.Accepted(KEY))
        }

        @DisplayName("주문 ID 를 6 자리로 채우고, 회원 ID 를 X-USER-ID 로 보낸다.")
        @Test
        fun padsOrderIdToSixDigits() {
            // arrange
            stubRequest(accepted())

            // act
            request(orderId = 1L)

            // assert
            verify(
                postRequestedFor(urlPathEqualTo(PAYMENTS))
                    .withHeader("X-USER-ID", equalTo("7"))
                    .withRequestBody(matchingJsonPath("$.orderId", equalTo("000001")))
                    .withRequestBody(matchingJsonPath("$.amount", equalTo("5000")))
                    .withRequestBody(matchingJsonPath("$.callbackUrl", equalTo("http://localhost:8080/api/v1/payments/callback"))),
            )
        }

        /**
         * 검토 초점 1. 서킷이 재시도 바깥이면 세 번의 시도가 서킷에는 실패 1 건이다. 3 건이면 aspect-order 설정이 빠져
         * 기본 순서 Retry( CircuitBreaker ) 로 돌아간 것이다 — 그러면 정상 PG 에서도 서킷이 열린다. (설계 6.3 · 6.4 장)
         */
        @DisplayName("5xx 가 세 번 나면, 정확히 세 번 시도하고 Unknown 이며 서킷에는 실패 1 건으로 센다.")
        @Test
        fun retriesThreeTimes_andCountsOneCircuitFailure() {
            // arrange
            stubRequest(failure(500))

            // act
            val result = request()

            // assert
            assertAll(
                { assertThat(result).isEqualTo(PgRequestResult.Unknown) },
                { requestCount(3) },
                { assertThat(circuit("pgRequest").metrics.numberOfFailedCalls).isEqualTo(1) },
            )
        }

        @DisplayName("5xx 뒤에 접수되면, 두 번 시도하고 Accepted 이며 서킷에는 성공 1 건으로 센다.")
        @Test
        fun returnsAccepted_whenSecondAttemptSucceeds() {
            // arrange
            stubFor(
                post(urlPathEqualTo(PAYMENTS)).inScenario("retry")
                    .whenScenarioStateIs(Scenario.STARTED)
                    .willReturn(failure(500))
                    .willSetStateTo("recovered"),
            )
            stubFor(
                post(urlPathEqualTo(PAYMENTS)).inScenario("retry")
                    .whenScenarioStateIs("recovered")
                    .willReturn(accepted()),
            )

            // act
            val result = request()

            // assert
            assertAll(
                { assertThat(result).isEqualTo(PgRequestResult.Accepted(KEY)) },
                { requestCount(2) },
                { assertThat(circuit("pgRequest").metrics.numberOfFailedCalls).isZero() },
                { assertThat(circuit("pgRequest").metrics.numberOfSuccessfulCalls).isEqualTo(1) },
            )
        }

        /**
         * 타임아웃은 PG 에 이미 저장됐을 수 있어 재시도하면 거래가 하나 더 생긴다. (설계 6.2 장)
         */
        @DisplayName("응답이 1 초를 넘으면, 재시도하지 않고 약 1 초 만에 Unknown 이다.")
        @Test
        fun returnsUnknownWithoutRetry_whenReadTimesOut() {
            // arrange
            stubRequest(accepted().withFixedDelay(1_500))

            // act
            val startedAt = System.currentTimeMillis()
            val result = request()
            val elapsed = System.currentTimeMillis() - startedAt

            // assert
            assertAll(
                { assertThat(result).isEqualTo(PgRequestResult.Unknown) },
                { requestCount(1) },
                // 상한은 넉넉히 둔다. 컨텍스트의 첫 Feign 호출이면 클라이언트 초기화 시간이 섞인다.
                { assertThat(elapsed).isBetween(900L, 2_000L) },
                { assertThat(circuit("pgRequest").metrics.numberOfFailedCalls).isEqualTo(1) },
            )
        }

        /**
         * 서킷이 재시도 바깥이라 실패한 "요청" 10 건(시도 30 번)에 열린다. 열린 뒤에는 재시도를 시작하기 전에 막으므로
         * 한 번도 보내지 않았다는 것이 확실하다 — "재시도 도중 서킷 OPEN" 은 생기지 않는다. (설계 6.3 · 6.4 장)
         */
        @DisplayName("실패한 요청이 10 건 쌓이면 서킷이 열리고, 그 뒤 요청은 보내지 않고 NotSent 다.")
        @Test
        fun opensAfterTenFailedRequests_andReturnsNotSent() {
            // arrange
            stubRequest(failure(500))
            repeat(10) { request() } // 시도 30 번

            // act
            val afterOpen = request()

            // assert
            assertAll(
                { assertThat(afterOpen).isEqualTo(PgRequestResult.NotSent) },
                { assertThat(circuit("pgRequest").state).isEqualTo(CircuitBreaker.State.OPEN) },
                { requestCount(30) },
            )
        }

        @DisplayName("4xx 는 재시도하지 않고, 아무리 쌓여도 서킷을 열지 않는다.")
        @Test
        fun keepsCircuitClosed_whenClientErrorsRepeat() {
            // arrange
            stubRequest(failure(400))

            // act
            val results = (1..60).map { request() }

            // assert
            assertAll(
                { assertThat(results).allMatch { it == PgRequestResult.Unknown } },
                { assertThat(circuit("pgRequest").state).isEqualTo(CircuitBreaker.State.CLOSED) },
                { requestCount(60) },
            )
        }
    }

    @DisplayName("거래를 조회할 때, ")
    @Nested
    inner class Query {
        @DisplayName("상세 조회는 금액까지 담아 Found 다.")
        @Test
        fun returnsFoundWithAmount() {
            // arrange
            stubFor(
                get(urlPathMatching("$PAYMENTS/.+")).willReturn(
                    okJson(
                        """{"meta":{"result":"SUCCESS","errorCode":null,"message":null},
                           "data":{"transactionKey":"$KEY","orderId":"000042","cardType":"SAMSUNG",
                                   "cardNo":"1234-5678-9814-1451","amount":5000,"status":"SUCCESS","reason":"정상 승인되었습니다."}}""",
                    ),
                ),
            )

            // act
            val result = paymentGateway.findTransaction(userId = 7L, transactionKey = KEY)

            // assert
            assertThat(result).isEqualTo(
                PgQueryResult.Found(PgTransaction(KEY, PgTransactionStatus.SUCCESS, "정상 승인되었습니다.", 5_000L)),
            )
        }

        @DisplayName("주문별 조회는 6 자리 주문 ID 로 묻고 거래 목록을 돌려준다.")
        @Test
        fun returnsTransactionsByOrder() {
            // arrange
            stubFor(
                get(urlPathEqualTo(PAYMENTS)).withQueryParam("orderId", equalTo("000042")).willReturn(
                    okJson(
                        """{"meta":{"result":"SUCCESS","errorCode":null,"message":null},
                           "data":{"orderId":"000042","transactions":[{"transactionKey":"$KEY","status":"PENDING","reason":null}]}}""",
                    ),
                ),
            )

            // act
            val result = paymentGateway.findTransactionsByOrder(userId = 7L, orderId = 42L)

            // assert
            assertAll(
                { assertThat(result).isEqualTo(PgQueryResult.Found(listOf(PgTransaction(KEY, PgTransactionStatus.PENDING, null, null)))) },
                { verify(getRequestedFor(urlPathEqualTo(PAYMENTS)).withHeader("X-USER-ID", equalTo("7"))) },
            )
        }

        @DisplayName("404 면 NotFound 이고, 서킷 실패로 세지 않는다.")
        @Test
        fun returnsNotFound_andDoesNotRecordFailure() {
            // arrange
            stubFor(get(urlPathEqualTo(PAYMENTS)).willReturn(failure(404)))

            // act
            val result = paymentGateway.findTransactionsByOrder(userId = 7L, orderId = 42L)

            // assert
            assertAll(
                { assertThat(result).isEqualTo(PgQueryResult.NotFound) },
                { assertThat(circuit("pgQuery").metrics.numberOfFailedCalls).isZero() },
            )
        }

        @DisplayName("5xx 면 재시도 없이 Unavailable 이다.")
        @Test
        fun returnsUnavailable_whenServerErrors() {
            // arrange
            stubFor(get(urlPathMatching("$PAYMENTS/.+")).willReturn(failure(500)))

            // act
            val result = paymentGateway.findTransaction(userId = 7L, transactionKey = KEY)

            // assert
            assertAll(
                { assertThat(result).isEqualTo(PgQueryResult.Unavailable) },
                { verify(1, getRequestedFor(urlPathMatching("$PAYMENTS/.+"))) },
            )
        }
    }
}
