package com.loopers.infrastructure.payment

import com.loopers.domain.payment.CardNo
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PgPaymentRequest
import com.loopers.domain.payment.PgRequestResult
import com.loopers.domain.product.Price
import feign.Request
import feign.RetryableException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

/**
 * 예외와 시도 횟수로 NotSent · Unknown 을 가르는 판정만 본다. (2026-10-13 결제 설계 6.4 장 판정 표)
 * "앞선 시도는 5xx, 다음 시도는 연결 거부" 는 실제 HTTP 로 만들 수 없어 PgResilientClient 를 바꿔 끼우고,
 * 그 클래스가 올리는 시도 카운터를 흉내 낸다.
 */
class PgPaymentGatewayTranslationTest {
    private val client: PgResilientClient = mock()
    private val gateway = PgPaymentGateway(
        client = client,
        properties = PgProperties(baseUrl = "http://pg", callbackUrl = "http://localhost:8080/api/v1/payments/callback"),
    )

    private fun request() = gateway.request(
        PgPaymentRequest(
            userId = 7L,
            orderId = 42L,
            cardType = CardType.SAMSUNG,
            cardNo = CardNo("1234-5678-9814-1451"),
            amount = Price(5_000),
        ),
    )

    /** Feign 이 IOException 을 감싸는 모양 그대로 만든다(SynchronousMethodHandler.errorExecuting). */
    private fun ioFailure(cause: Exception) = RetryableException(
        -1,
        cause.message,
        Request.HttpMethod.POST,
        cause,
        null as Long?,
        Request.create(Request.HttpMethod.POST, "http://pg/api/v1/payments", emptyMap(), ByteArray(0), StandardCharsets.UTF_8),
    )

    /** 시도를 attempts 번 한 것처럼 카운터를 올린 뒤 failure 를 던진다. */
    private fun failAfter(attempts: Int, failure: Exception) {
        whenever(client.requestPayment(any(), any(), any())).thenAnswer {
            it.getArgument<AtomicInteger>(2).addAndGet(attempts)
            throw failure
        }
    }

    @DisplayName("서킷이 막으면 NotSent 다.")
    @Test
    fun returnsNotSent_whenCircuitIsOpen() {
        // arrange
        failAfter(0, CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("pgRequest")))

        // act
        val result = request()

        // assert
        assertThat(result).isEqualTo(PgRequestResult.NotSent)
    }

    @DisplayName("첫 시도에서 연결이 거부되면 NotSent 다.")
    @Test
    fun returnsNotSent_whenFirstAttemptIsRefused() {
        // arrange
        failAfter(1, ioFailure(ConnectException("Connection refused")))

        // act
        val result = request()

        // assert
        assertThat(result).isEqualTo(PgRequestResult.NotSent)
    }

    /** 앞선 시도가 5xx 로 PG 에 닿았다. 그 시도가 PG 에 무엇을 남겼는지 모른다. (설계 4.2 장) */
    @DisplayName("앞선 시도 뒤에 연결이 거부되면 Unknown 이다.")
    @Test
    fun returnsUnknown_whenRefusedAfterEarlierAttempt() {
        // arrange
        failAfter(2, ioFailure(ConnectException("Connection refused")))

        // act
        val result = request()

        // assert
        assertThat(result).isEqualTo(PgRequestResult.Unknown)
    }

    /** 연결 타임아웃은 읽기 타임아웃과 같은 SocketTimeoutException 이라 메시지로만 구분된다. 메시지에 판정을 걸지 않는다. */
    @DisplayName("연결 타임아웃은 Unknown 이다.")
    @Test
    fun returnsUnknown_whenConnectTimesOut() {
        // arrange
        failAfter(1, ioFailure(SocketTimeoutException("Connect timed out")))

        // act
        val result = request()

        // assert
        assertThat(result).isEqualTo(PgRequestResult.Unknown)
    }
}
