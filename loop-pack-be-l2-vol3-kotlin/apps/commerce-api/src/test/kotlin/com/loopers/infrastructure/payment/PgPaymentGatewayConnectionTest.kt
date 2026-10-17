package com.loopers.infrastructure.payment

import com.loopers.domain.payment.CardNo
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentGateway
import com.loopers.domain.payment.PgPaymentRequest
import com.loopers.domain.payment.PgRequestResult
import com.loopers.domain.product.Price
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * 검토 초점 8. 실제로 연결이 거부될 때 Feign 이 ConnectException 을 cause 로 담아 던지는지 확인한다.
 * PgPaymentGateway 의 NotSent 판정이 이 사실에 기대므로, Feign 의 HTTP 클라이언트가 바뀌어 예외 모양이 달라지면 여기서 깨진다.
 * (2026-10-13 결제 설계 4.2 · 6.4 장)
 *
 * 1 번 포트에는 듣는 프로세스가 없다고 본다.
 */
@SpringBootTest(properties = ["pg.base-url=http://localhost:1"])
class PgPaymentGatewayConnectionTest @Autowired constructor(
    private val paymentGateway: PaymentGateway,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
) {
    @BeforeEach
    fun setUp() {
        circuitBreakerRegistry.allCircuitBreakers.forEach { it.reset() }
    }

    @DisplayName("PG 연결이 거부되면, 보내지 않은 것이 확실하므로 NotSent 이고 서킷에는 실패 1 건으로 센다.")
    @Test
    fun returnsNotSent_whenConnectionRefused() {
        // act
        val result = paymentGateway.request(
            PgPaymentRequest(
                userId = 7L,
                orderId = 42L,
                cardType = CardType.SAMSUNG,
                cardNo = CardNo("1234-5678-9814-1451"),
                amount = Price(5_000),
            ),
        )

        // assert
        assertAll(
            { assertThat(result).isEqualTo(PgRequestResult.NotSent) },
            { assertThat(circuitBreakerRegistry.circuitBreaker("pgRequest").metrics.numberOfFailedCalls).isEqualTo(1) },
        )
    }
}
