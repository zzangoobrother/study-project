package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentGateway
import com.loopers.domain.payment.PgPaymentRequest
import com.loopers.domain.payment.PgQueryResult
import com.loopers.domain.payment.PgRequestResult
import com.loopers.domain.payment.PgTransaction
import com.loopers.domain.payment.PgTransactionStatus
import feign.FeignException
import feign.RetryableException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.ConnectException
import java.util.concurrent.atomic.AtomicInteger

/**
 * PaymentGateway 구현. Feign · Resilience4j 예외를 결과 타입으로 번역하는 유일한 자리다. (2026-10-13 결제 설계 4.2 · 6.4 장)
 *
 * 즉시 실패(NotSent)로 번역하는 것은 보내지 않은 것이 확실한 두 경우 — 서킷이 막았을 때와 첫 시도의 연결이 거부됐을 때 — 뿐이다.
 * 5xx 도 Unknown 이다 — 실제 PG 는 어디까지 처리했는지 장담할 수 없다. 시뮬레이터의 구현 세부에 기대지 않는다.
 *
 * 그런데도 5xx 를 재시도하는 것은 모순이 아니라 비용이 다른 두 판단이다. 분류를 보수적으로 하는 것은 싸고(동기화가 정리한다),
 * 재시도는 성공률을 올리는 대신 "PG 의 5xx 는 거래를 남기지 않았다" 는 가정에 건다. 실제 PG 에 붙일 때는 멱등 키를 확인하고,
 * 없으면 application.yml 의 pgRequest retry-exceptions 에서 5xx 를 뺀다. (설계 6.2 · 9.7 장)
 */
@Component
class PgPaymentGateway(
    private val client: PgResilientClient,
    private val properties: PgProperties,
) : PaymentGateway {
    private val log = LoggerFactory.getLogger(PgPaymentGateway::class.java)

    override fun request(request: PgPaymentRequest): PgRequestResult {
        val attempts = AtomicInteger(0)
        return try {
            val response = client.requestPayment(
                userId = request.userId.toString(),
                request = PgDto.PaymentRequest(
                    orderId = PgDto.toPgOrderId(request.orderId),
                    cardType = request.cardType.name,
                    cardNo = request.cardNo.value,
                    amount = request.amount.value,
                    callbackUrl = properties.callbackUrl,
                ),
                attempts = attempts,
            )
            response.data?.transactionKey
                ?.let { PgRequestResult.Accepted(it) }
                ?: run {
                    log.error("[orderId = {}] PG 가 200 을 주었지만 거래 키가 없습니다.", request.orderId)
                    PgRequestResult.Unknown
                }
        } catch (e: CallNotPermittedException) {
            // 서킷이 재시도 바깥이라 이 예외는 첫 시도 전에만 난다. 한 번도 보내지 않았다. (설계 6.4 장)
            log.warn("[orderId = {}] 서킷이 열려 PG 를 호출하지 않았습니다.", request.orderId)
            PgRequestResult.NotSent
        } catch (e: RetryableException) {
            // 연결 거부는 TCP 연결도 맺지 못한 것이라 요청이 나가지 않았다. 단 앞선 시도가 5xx 로 PG 에 닿았다면 그 결과를 모른다.
            // 연결 타임아웃은 읽기 타임아웃과 같은 SocketTimeoutException 이라 메시지로만 구분되므로 Unknown 으로 둔다. (설계 4.2 장)
            if (e.cause is ConnectException && attempts.get() == 1) {
                log.warn("[orderId = {}] PG 연결이 거부되어 결제 요청을 보내지 못했습니다.", request.orderId)
                PgRequestResult.NotSent
            } else {
                log.warn("[orderId = {}] PG 결제 요청 결과를 알 수 없습니다. 시도 {} 회. {}", request.orderId, attempts.get(), e.toString())
                PgRequestResult.Unknown
            }
        } catch (e: FeignException.FeignClientException) {
            // 입력은 우리 쪽에서 이미 검증했으므로 정상 경로에선 나오지 않는다. 요청 형식 버그의 신호다.
            log.error("[orderId = {}] PG 가 결제 요청을 거부했습니다. status = {}", request.orderId, e.status(), e)
            PgRequestResult.Unknown
        } catch (e: Exception) {
            log.warn("[orderId = {}] PG 결제 요청 결과를 알 수 없습니다. {}", request.orderId, e.toString())
            PgRequestResult.Unknown
        }
    }

    override fun findTransaction(userId: Long, transactionKey: String): PgQueryResult<PgTransaction> = query {
        client.getTransaction(userId.toString(), transactionKey).data
            ?.let { PgQueryResult.Found(PgTransaction(it.transactionKey, toStatus(it.status), it.reason, it.amount)) }
            ?: PgQueryResult.NotFound
    }

    override fun findTransactionsByOrder(userId: Long, orderId: Long): PgQueryResult<List<PgTransaction>> = query {
        client.getTransactionsByOrder(userId.toString(), PgDto.toPgOrderId(orderId)).data
            ?.let { data ->
                // 목록 응답에는 금액이 없다. 확정 전 금액 검증은 상세 조회로 한다. (설계 5.2 장)
                PgQueryResult.Found(
                    data.transactions.map { PgTransaction(it.transactionKey, toStatus(it.status), it.reason, amount = null) },
                )
            }
            ?: PgQueryResult.NotFound
    }

    /** 조회 실패는 "모른다" 다. 호출자는 상태를 바꾸지 않고 다음 주기를 기다린다. (설계 5.2 장) */
    private fun <T> query(block: () -> PgQueryResult<T>): PgQueryResult<T> = try {
        block()
    } catch (e: FeignException.NotFound) {
        PgQueryResult.NotFound
    } catch (e: Exception) {
        log.warn("PG 조회에 실패했습니다. {}", e.toString())
        PgQueryResult.Unavailable
    }

    /** 모르는 상태는 IllegalArgumentException 으로 query 의 catch 에 걸려 Unavailable 이 된다 — 확정하지 않는다. */
    private fun toStatus(value: String): PgTransactionStatus = PgTransactionStatus.valueOf(value)
}
