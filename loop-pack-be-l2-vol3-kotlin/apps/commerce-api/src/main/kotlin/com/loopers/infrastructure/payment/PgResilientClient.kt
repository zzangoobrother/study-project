package com.loopers.infrastructure.payment

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

/**
 * 재시도 · 서킷만 붙이는 층. 예외를 해석하지 않고 그대로 던진다. (2026-10-13 결제 설계 6.4 장)
 *
 * PgPaymentGateway 와 나눈 이유 — 애노테이션은 AOP 프록시로 동작해 같은 클래스 안의 호출에는 걸리지 않는다.
 * fallbackMethod 를 쓰지 않는 이유 — 재시도와 서킷 양쪽의 fallback 이 얽히면 어디서 무엇이 처리됐는지 읽히지 않는다.
 *
 * 감싸는 순서는 CircuitBreaker( Retry( 호출 ) ) 다 — 애노테이션 기본값의 반대라 application.yml 의 aspect-order 로 뒤집었다.
 * 서킷은 재시도를 다 마친 결과를 한 건으로 센다. 재시도 하나하나를 세면 평소 시도 실패율 40% 때문에 정상인 PG 에서도
 * 서킷이 열린다. (설계 6.3 · 6.4 장)
 */
@Component
class PgResilientClient(
    private val pgClient: PgClient,
) {
    /**
     * attempts 는 메서드 본문 첫 줄에서 올린다. 재시도가 본문을 다시 부를 때마다 오르므로 실제로 나간 시도 수다.
     * PgPaymentGateway 가 연결 거부가 첫 시도였는지 가리는 데 쓴다 — 앞선 시도가 PG 에 닿았다면 NotSent 가 아니다.
     * 이 줄을 pgClient 호출 뒤로 옮기면 실패한 시도가 세어지지 않아 NotSent 오판이 생긴다. (설계 4.2 장)
     */
    @CircuitBreaker(name = "pgRequest")
    @Retry(name = "pgRequest")
    fun requestPayment(
        userId: String,
        request: PgDto.PaymentRequest,
        attempts: AtomicInteger,
    ): PgDto.Response<PgDto.TransactionResponse> {
        attempts.incrementAndGet()
        return pgClient.requestPayment(userId, request)
    }

    /** 조회는 재시도하지 않는다. 스케줄러가 다음 주기에 다시 묻는다. (설계 6.2 장) */
    @CircuitBreaker(name = "pgQuery")
    fun getTransaction(userId: String, transactionKey: String): PgDto.Response<PgDto.TransactionDetailResponse> =
        pgClient.getTransaction(userId, transactionKey)

    @CircuitBreaker(name = "pgQuery")
    fun getTransactionsByOrder(userId: String, orderId: String): PgDto.Response<PgDto.OrderResponse> =
        pgClient.getTransactionsByOrder(userId, orderId)
}
