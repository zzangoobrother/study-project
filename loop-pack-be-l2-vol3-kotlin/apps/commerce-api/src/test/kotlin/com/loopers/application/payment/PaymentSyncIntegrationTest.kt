package com.loopers.application.payment

import com.loopers.domain.order.OrderItemModel
import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.order.Quantity
import com.loopers.domain.payment.CardNo
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentCommand
import com.loopers.domain.payment.PaymentGateway
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgQueryResult
import com.loopers.domain.payment.PgRequestResult
import com.loopers.domain.payment.PgTransaction
import com.loopers.domain.payment.PgTransactionStatus
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductName
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserName
import com.loopers.domain.user.UserService
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.ZonedDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 설계 5.2 장 표의 행마다 테스트가 하나다. 표를 바꾸면 이 클래스도 같이 바뀌어야 한다.
 */
@SpringBootTest
class PaymentSyncIntegrationTest @Autowired constructor(
    private val paymentFacade: PaymentFacade,
    private val paymentService: PaymentService,
    private val orderService: OrderService,
    private val userService: UserService,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockitoBean
    lateinit var paymentGateway: PaymentGateway

    companion object {
        private const val LOGIN_ID = "loopers01"
        private const val KEY = "20251013:TR:abc123"
        private const val OLD_KEY = "20251013:TR:old000"
        private const val AMOUNT = 5_000L
    }

    /** REQUESTED 결제는 생성 후 6 s 동안 PG 를 조회하지 않는다(설계 5.2 장). 그 유예를 넘긴 "지금" 이다. */
    private fun afterInFlight(): ZonedDateTime = ZonedDateTime.now().plusSeconds(10)

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun signUp(): Long {
        userService.signUp(
            UserCommand.SignUp(
                loginId = LoginId(LOGIN_ID),
                password = RawPassword("Loopers1!"),
                name = UserName("홍길동"),
                birthDate = BirthDate.from("1990-01-01"),
                email = Email("$LOGIN_ID@loopers.com"),
            ),
        )
        return userService.getUser(LoginId(LOGIN_ID))!!.id
    }

    private fun placeOrder(): OrderModel = orderService.place(
        userId = signUp(),
        items = listOf(
            OrderItemModel.create(productId = 1L, productName = ProductName("운동화"), unitPrice = Price(AMOUNT), quantity = Quantity(1)),
        ),
    )

    private fun pay(orderId: Long, result: PgRequestResult): PaymentInfo {
        whenever(paymentGateway.request(any())).thenReturn(result)
        return paymentFacade.request(
            PaymentCommand.Request(LoginId(LOGIN_ID), orderId, CardType.SAMSUNG, CardNo("1234-5678-9814-1451")),
        )
    }

    private fun tx(
        key: String = KEY,
        status: PgTransactionStatus,
        amount: Long? = AMOUNT,
        reason: String? = null,
        orderId: Long? = null,
    ) = PgTransaction(transactionKey = key, status = status, reason = reason, amount = amount, orderId = orderId)

    private fun stubDetail(result: PgQueryResult<PgTransaction>) {
        whenever(paymentGateway.findTransaction(any(), any())).thenReturn(result)
    }

    private fun stubByOrder(result: PgQueryResult<List<PgTransaction>>) {
        whenever(paymentGateway.findTransactionsByOrder(any(), any())).thenReturn(result)
    }

    private fun paymentOf(info: PaymentInfo) = paymentService.getPayment(info.paymentId)!!

    private fun orderStatusOf(orderId: Long) = orderService.getOrder(orderId)!!.status

    @DisplayName("대기 중인 결제를 동기화할 때, ")
    @Nested
    inner class Pending {
        @DisplayName("PG 가 성공이면, 결제 성공 · 주문 결제 완료다.")
        @Test
        fun succeeds_whenPgSucceeded() {
            // arrange
            val order = placeOrder()
            val info = pay(order.id, PgRequestResult.Accepted(KEY))
            stubDetail(PgQueryResult.Found(tx(status = PgTransactionStatus.SUCCESS)))

            // act
            paymentFacade.sync(info.paymentId)

            // assert
            assertAll(
                { assertThat(paymentOf(info).status).isEqualTo(PaymentStatus.SUCCESS) },
                { assertThat(orderStatusOf(order.id)).isEqualTo(OrderStatus.PAID) },
            )
        }

        @DisplayName("PG 가 실패면, PG 의 사유로 결제 실패 · 주문 결제 실패다.")
        @Test
        fun fails_whenPgFailed() {
            // arrange
            val order = placeOrder()
            val info = pay(order.id, PgRequestResult.Accepted(KEY))
            stubDetail(PgQueryResult.Found(tx(status = PgTransactionStatus.FAILED, reason = "한도초과입니다. 다른 카드를 선택해주세요.")))

            // act
            paymentFacade.sync(info.paymentId)

            // assert
            assertAll(
                { assertThat(paymentOf(info).status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(paymentOf(info).failureReason).isEqualTo("한도초과입니다. 다른 카드를 선택해주세요.") },
                { assertThat(orderStatusOf(order.id)).isEqualTo(OrderStatus.PAYMENT_FAILED) },
            )
        }

        @DisplayName("PG 도 아직 처리 중이면, 아무것도 바뀌지 않는다.")
        @Test
        fun staysPending_whenPgStillPending() {
            // arrange
            val order = placeOrder()
            val info = pay(order.id, PgRequestResult.Accepted(KEY))
            stubDetail(PgQueryResult.Found(tx(status = PgTransactionStatus.PENDING)))

            // act
            paymentFacade.sync(info.paymentId)

            // assert
            assertAll(
                { assertThat(paymentOf(info).status).isEqualTo(PaymentStatus.PENDING) },
                { assertThat(orderStatusOf(order.id)).isEqualTo(OrderStatus.PAYING) },
            )
        }

        @DisplayName("PG 조회에 실패하면, 아무것도 바뀌지 않는다.")
        @Test
        fun staysPending_whenPgUnavailable() {
            // arrange
            val order = placeOrder()
            val info = pay(order.id, PgRequestResult.Accepted(KEY))
            stubDetail(PgQueryResult.Unavailable)

            // act
            paymentFacade.sync(info.paymentId)

            // assert
            assertThat(paymentOf(info).status).isEqualTo(PaymentStatus.PENDING)
        }

        /** "모르는 것을 실패로 확정하지 않는다" — 상세 404 도 모르는 것이다. (설계 9.6 장) */
        @DisplayName("PG 상세 조회가 거래 없음이면, 아무것도 바뀌지 않는다.")
        @Test
        fun staysPending_whenDetailNotFound() {
            // arrange
            val order = placeOrder()
            val info = pay(order.id, PgRequestResult.Accepted(KEY))
            stubDetail(PgQueryResult.NotFound)

            // act
            paymentFacade.sync(info.paymentId, now = ZonedDateTime.now().plusMinutes(2))

            // assert
            assertAll(
                { assertThat(paymentOf(info).status).isEqualTo(PaymentStatus.PENDING) },
                { assertThat(orderStatusOf(order.id)).isEqualTo(OrderStatus.PAYING) },
            )
        }

        /**
         * 검토 초점 10. 위조나 버그의 신호라 PG 의 결과를 반영하지 않는다. 그렇다고 그대로 두면 상태가 영영 바뀌지 않아
         * 매 주기 동기화 대상으로 남으므로 실패로 닫고 사람에게 넘긴다. (2026-10-13 결제 설계 5.2 · 9.5 장)
         */
        @DisplayName("PG 의 금액이 다르면, PG 결과와 무관하게 결제 실패 · 주문 결제 실패로 닫고 거래 키는 기록한다.")
        @Test
        fun closesAsFailed_whenAmountMismatches() {
            // arrange
            val order = placeOrder()
            val info = pay(order.id, PgRequestResult.Accepted(KEY))
            stubDetail(PgQueryResult.Found(tx(status = PgTransactionStatus.SUCCESS, amount = 1L)))

            // act
            paymentFacade.sync(info.paymentId)

            // assert
            assertAll(
                { assertThat(paymentOf(info).status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(paymentOf(info).failureReason).isEqualTo("PG 결제 금액이 주문 금액과 다릅니다.") },
                { assertThat(paymentOf(info).transactionKey).isEqualTo(KEY) },
                { assertThat(orderStatusOf(order.id)).isEqualTo(OrderStatus.PAYMENT_FAILED) },
            )
        }

        /**
         * 2026-10-22 측정이 드러낸 키 충돌(11.4 장). PG 가 키를 중복 발급해 이 키의 상세가 다른 주문의 거래로 덮어써졌다.
         * 그 결과로 확정하면 남의 거래로 이 결제가 확정된다. 그대로 두면 매 주기 다시 뽑히므로 닫는다. (설계 5.2 · 9.5 장)
         */
        @DisplayName("PG 상세의 주문이 결제 주문과 다르면, PG 결과와 무관하게 결제 실패 · 주문 결제 실패로 닫고 예외는 없다.")
        @Test
        fun closesAsFailed_whenOrderMismatches() {
            // arrange
            val order = placeOrder()
            val info = pay(order.id, PgRequestResult.Accepted(KEY))
            stubDetail(PgQueryResult.Found(tx(status = PgTransactionStatus.SUCCESS, orderId = order.id + 1)))

            // act
            paymentFacade.sync(info.paymentId)

            // assert
            assertAll(
                { assertThat(paymentOf(info).status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(paymentOf(info).failureReason).isEqualTo("PG 거래의 주문이 결제 주문과 다릅니다.") },
                { assertThat(paymentOf(info).transactionKey).isEqualTo(KEY) },
                { assertThat(orderStatusOf(order.id)).isEqualTo(OrderStatus.PAYMENT_FAILED) },
            )
        }

        @DisplayName("PG 상세의 주문이 결제 주문과 같으면, 평소처럼 확정한다.")
        @Test
        fun confirms_whenOrderMatches() {
            // arrange
            val order = placeOrder()
            val info = pay(order.id, PgRequestResult.Accepted(KEY))
            stubDetail(PgQueryResult.Found(tx(status = PgTransactionStatus.SUCCESS, orderId = order.id)))

            // act
            paymentFacade.sync(info.paymentId)

            // assert
            assertThat(paymentOf(info).status).isEqualTo(PaymentStatus.SUCCESS)
        }

        @DisplayName("콜백이 다른 주문의 거래로 덮어써진 키로 오면, 대기 결제를 실패로 닫고 예외는 없다.")
        @Test
        fun closesAsFailed_whenCallbackKeyWasOverwrittenByOtherOrder() {
            // arrange
            val order = placeOrder()
            val info = pay(order.id, PgRequestResult.Accepted(KEY))
            stubDetail(PgQueryResult.Found(tx(status = PgTransactionStatus.SUCCESS, orderId = order.id + 1)))

            // act
            paymentFacade.handleCallback(KEY, order.id.toString().padStart(6, '0'))

            // assert
            assertAll(
                { assertThat(paymentOf(info).status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(orderStatusOf(order.id)).isEqualTo(OrderStatus.PAYMENT_FAILED) },
            )
        }
    }

    @DisplayName("요청됨 결제를 동기화할 때, ")
    @Nested
    inner class Requested {
        /**
         * 검토 초점 11. 요청이 아직 PG 로 가는 중일 수 있다. 이때 모르는 키(고아 거래일 수 있다)를 연결하면
         * 뒤이어 이번 요청의 거래까지 승인돼 이중 결제가 된다. (2026-10-13 결제 설계 5.2 · 9.5 장, 2026-10-17 검토)
         */
        @DisplayName("생성 후 6 초가 안 지났으면, PG 를 조회하지 않는다.")
        @Test
        fun skipsPg_whileRequestMayBeInFlight() {
            // arrange
            val order = placeOrder()
            val info = pay(order.id, PgRequestResult.Unknown)
            stubByOrder(PgQueryResult.Found(listOf(tx(status = PgTransactionStatus.SUCCESS, amount = null))))

            // act
            paymentFacade.sync(info.paymentId)

            // assert
            assertAll(
                { assertThat(paymentOf(info).status).isEqualTo(PaymentStatus.REQUESTED) },
                { assertThat(paymentOf(info).transactionKey).isNull() },
                { verify(paymentGateway, never()).findTransactionsByOrder(any(), any()) },
            )
        }

        @DisplayName("PG 에 모르는 키의 거래가 있으면, 그 키를 연결하고 결과를 반영한다.")
        @Test
        fun linksUnknownKey_andConfirms() {
            // arrange
            val order = placeOrder()
            val info = pay(order.id, PgRequestResult.Unknown)
            stubByOrder(PgQueryResult.Found(listOf(tx(status = PgTransactionStatus.SUCCESS, amount = null))))
            stubDetail(PgQueryResult.Found(tx(status = PgTransactionStatus.SUCCESS)))

            // act
            paymentFacade.sync(info.paymentId, now = afterInFlight())

            // assert
            assertAll(
                { assertThat(paymentOf(info).transactionKey).isEqualTo(KEY) },
                { assertThat(paymentOf(info).status).isEqualTo(PaymentStatus.SUCCESS) },
                { assertThat(orderStatusOf(order.id)).isEqualTo(OrderStatus.PAID) },
            )
        }

        @DisplayName("PG 에 거래가 없고 1 분이 안 지났으면, 아무것도 바뀌지 않는다.")
        @Test
        fun staysRequested_whenNotFoundWithinOneMinute() {
            // arrange
            val order = placeOrder()
            val info = pay(order.id, PgRequestResult.Unknown)
            stubByOrder(PgQueryResult.NotFound)

            // act
            paymentFacade.sync(info.paymentId, now = afterInFlight())

            // assert
            assertAll(
                { assertThat(paymentOf(info).status).isEqualTo(PaymentStatus.REQUESTED) },
                { assertThat(orderStatusOf(order.id)).isEqualTo(OrderStatus.PAYING) },
                { verify(paymentGateway).findTransactionsByOrder(any(), any()) },
            )
        }

        @DisplayName("PG 에 거래가 없고 1 분이 지났으면, 결제 실패 · 주문 결제 실패다.")
        @Test
        fun fails_whenNotFoundAfterOneMinute() {
            // arrange
            val order = placeOrder()
            val info = pay(order.id, PgRequestResult.Unknown)
            stubByOrder(PgQueryResult.NotFound)

            // act
            paymentFacade.sync(info.paymentId, now = ZonedDateTime.now().plusMinutes(2))

            // assert
            assertAll(
                { assertThat(paymentOf(info).status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(orderStatusOf(order.id)).isEqualTo(OrderStatus.PAYMENT_FAILED) },
            )
        }

        /**
         * 검토 초점 3. 과거 실패 결제의 키(OLD_KEY)는 이미 우리 결제 건에 연결돼 있으므로 이번 요청의 거래가 아니다. (설계 5.2 장)
         */
        @DisplayName("PG 에 이미 아는 키의 거래만 있고 1 분이 지났으면, 결제 실패다.")
        @Test
        fun failsRequested_whenOnlyKnownKeysExistAfterOneMinute() {
            // arrange
            val order = placeOrder()
            val first = pay(order.id, PgRequestResult.Accepted(OLD_KEY))
            stubDetail(PgQueryResult.Found(tx(key = OLD_KEY, status = PgTransactionStatus.FAILED, reason = "잘못된 카드입니다.")))
            paymentFacade.sync(first.paymentId)
            val second = pay(order.id, PgRequestResult.Unknown)
            stubByOrder(PgQueryResult.Found(listOf(tx(key = OLD_KEY, status = PgTransactionStatus.FAILED, amount = null))))

            // act
            paymentFacade.sync(second.paymentId, now = ZonedDateTime.now().plusMinutes(2))

            // assert
            assertAll(
                { assertThat(paymentOf(second).status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(paymentOf(second).transactionKey).isNull() },
                { assertThat(paymentOf(first).transactionKey).isEqualTo(OLD_KEY) },
            )
        }

        /**
         * 검토 초점 9. 첫 결제를 1 분 뒤 포기했는데 그 요청이 뒤늦게 승인되면(고아 거래 OLD_KEY) 어느 결제 건에도 연결돼 있지 않다.
         * 재결제의 PG 목록에는 모르는 키가 고아와 이번 거래, 둘이 된다. 어느 쪽이 이번 요청인지 알 수 없으므로 연결하지 않는다.
         * (2026-10-13 결제 설계 5.2 · 9.5 장)
         */
        @DisplayName("PG 에 모르는 키의 거래가 둘 이상이면, 연결하지 않고 1 분 뒤 거래를 특정할 수 없다는 사유로 결제 실패다.")
        @Test
        fun doesNotLink_whenUnknownKeysAreAmbiguous() {
            // arrange
            val order = placeOrder()
            val givenUp = pay(order.id, PgRequestResult.Unknown)
            stubByOrder(PgQueryResult.NotFound)
            paymentFacade.sync(givenUp.paymentId, now = ZonedDateTime.now().plusMinutes(2))
            val retried = pay(order.id, PgRequestResult.Unknown)
            stubByOrder(
                PgQueryResult.Found(
                    listOf(
                        tx(key = OLD_KEY, status = PgTransactionStatus.SUCCESS, amount = null),
                        tx(key = KEY, status = PgTransactionStatus.SUCCESS, amount = null),
                    ),
                ),
            )

            // act
            paymentFacade.sync(retried.paymentId, now = ZonedDateTime.now().plusMinutes(2))

            // assert
            assertAll(
                { assertThat(paymentOf(givenUp).status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(paymentOf(retried).status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(paymentOf(retried).failureReason).isEqualTo("PG 거래를 특정할 수 없습니다. 고객센터에 문의해주세요.") },
                { assertThat(paymentOf(retried).transactionKey).isNull() },
                { assertThat(orderStatusOf(order.id)).isEqualTo(OrderStatus.PAYMENT_FAILED) },
                { verify(paymentGateway, never()).findTransaction(any(), any()) },
            )
        }

        @DisplayName("PG 에 모르는 키의 거래가 둘 이상이고 1 분이 안 지났으면, 연결하지 않고 아무것도 바뀌지 않는다.")
        @Test
        fun staysRequested_whenUnknownKeysAreAmbiguousWithinOneMinute() {
            // arrange
            val order = placeOrder()
            val info = pay(order.id, PgRequestResult.Unknown)
            stubByOrder(
                PgQueryResult.Found(
                    listOf(
                        tx(key = OLD_KEY, status = PgTransactionStatus.SUCCESS, amount = null),
                        tx(key = KEY, status = PgTransactionStatus.SUCCESS, amount = null),
                    ),
                ),
            )

            // act
            paymentFacade.sync(info.paymentId, now = afterInFlight())

            // assert
            assertAll(
                { assertThat(paymentOf(info).status).isEqualTo(PaymentStatus.REQUESTED) },
                { assertThat(paymentOf(info).transactionKey).isNull() },
                { assertThat(orderStatusOf(order.id)).isEqualTo(OrderStatus.PAYING) },
                { verify(paymentGateway, never()).findTransaction(any(), any()) },
            )
        }

        /**
         * 2026-10-22 측정이 드러낸 키 충돌(11.4 장). 연결하려는 키가 다른 주문의 결제 P1 에 이미 연결돼 있다.
         * 그대로 연결하면 커밋에서 uk_payments_transaction_key 위반으로 매 주기 ERROR 가 된다. (설계 5.2 · 9.5 장)
         */
        @DisplayName("연결하려는 키가 다른 결제에 이미 연결돼 있으면, 키를 기록하지 않고 거래를 특정할 수 없다는 사유로 결제 실패다.")
        @Test
        fun closesAsFailed_whenKeyBelongsToOtherPayment() {
            // arrange
            val order = placeOrder()
            val owner = pay(order.id, PgRequestResult.Accepted(KEY))
            val otherOrder = orderService.place(
                userId = userService.getUser(LoginId(LOGIN_ID))!!.id,
                items = listOf(
                    OrderItemModel.create(productId = 2L, productName = ProductName("슬리퍼"), unitPrice = Price(AMOUNT), quantity = Quantity(1)),
                ),
            )
            val info = pay(otherOrder.id, PgRequestResult.Unknown)
            stubByOrder(PgQueryResult.Found(listOf(tx(status = PgTransactionStatus.PENDING, amount = null))))
            stubDetail(PgQueryResult.Found(tx(status = PgTransactionStatus.SUCCESS, orderId = otherOrder.id)))

            // act
            paymentFacade.sync(info.paymentId, now = afterInFlight())

            // assert
            assertAll(
                { assertThat(paymentOf(info).status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(paymentOf(info).failureReason).isEqualTo("PG 거래를 특정할 수 없습니다. 고객센터에 문의해주세요.") },
                { assertThat(paymentOf(info).transactionKey).isNull() },
                { assertThat(orderStatusOf(otherOrder.id)).isEqualTo(OrderStatus.PAYMENT_FAILED) },
                { assertThat(paymentOf(owner).status).isEqualTo(PaymentStatus.PENDING) },
                { assertThat(paymentOf(owner).transactionKey).isEqualTo(KEY) },
                { assertThat(orderStatusOf(order.id)).isEqualTo(OrderStatus.PAYING) },
            )
        }

        @DisplayName("PG 조회에 실패하면, 1 분이 지났어도 아무것도 바뀌지 않는다.")
        @Test
        fun staysRequested_whenPgUnavailable() {
            // arrange
            val order = placeOrder()
            val info = pay(order.id, PgRequestResult.Unknown)
            stubByOrder(PgQueryResult.Unavailable)

            // act
            paymentFacade.sync(info.paymentId, now = ZonedDateTime.now().plusMinutes(2))

            // assert
            assertThat(paymentOf(info).status).isEqualTo(PaymentStatus.REQUESTED)
        }
    }

    @DisplayName("확정된 결제를 동기화하면, PG 를 조회하지 않는다.")
    @Test
    fun skipsPg_whenAlreadyFinal() {
        // arrange
        val order = placeOrder()
        val info = pay(order.id, PgRequestResult.NotSent)

        // act
        paymentFacade.sync(info.paymentId)

        // assert
        assertAll(
            { verify(paymentGateway, never()).findTransaction(any(), any()) },
            { verify(paymentGateway, never()).findTransactionsByOrder(any(), any()) },
        )
    }

    /**
     * 검토 초점 6. 한쪽은 "PG 에 없다(1 분 경과)" 로 포기하고, 다른 쪽은 "PG 가 승인했다" 로 확정하려는 경합이다.
     * @Version 이 없으면 결제는 마지막 커밋이, 주문은 먼저 UPDATE 한 쪽이 이겨 둘이 어긋난 채 확정될 수 있다. (2026-10-13 결제 설계 3.1 · 5.2 장)
     *
     * 두 트랜잭션이 매번 겹친다는 보장은 없다. 그래서 결과가 아니라 불변식을 단언한다 — 어느 순서로 끝나도
     * 결제와 주문은 같은 결과를 가리키고, 진 쪽의 낙관적 락 예외가 sync 밖으로 새지 않는다(future.get 이 예외 없이 끝난다).
     * 정확한 재현은 PaymentModelPersistenceTest.rejectsStaleUpdate 가 맡는다.
     */
    @DisplayName("두 경로가 같은 결제를 서로 다른 결과로 동시에 확정하려 해도, 결제와 주문의 결과가 어긋나지 않는다.")
    @Test
    fun keepsPaymentAndOrderConsistent_whenConfirmedConcurrently() {
        // arrange
        val order = placeOrder()
        val info = pay(order.id, PgRequestResult.Unknown)
        // 먼저 묻는 쪽은 "없음" 을 받아 포기하고, 나중에 묻는 쪽은 승인된 거래를 받아 확정한다.
        whenever(paymentGateway.findTransactionsByOrder(any(), any())).thenReturn(
            PgQueryResult.NotFound,
            PgQueryResult.Found(listOf(tx(status = PgTransactionStatus.SUCCESS, amount = null))),
        )
        stubDetail(PgQueryResult.Found(tx(status = PgTransactionStatus.SUCCESS)))
        val later = ZonedDateTime.now().plusMinutes(2)
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)

        // act
        val futures = (1..2).map {
            executor.submit<Unit> {
                ready.countDown()
                start.await()
                paymentFacade.sync(info.paymentId, now = later)
            }
        }
        ready.await()
        start.countDown()
        futures.forEach { it.get(5, TimeUnit.SECONDS) }
        executor.shutdown()

        // assert
        val paymentStatus = paymentOf(info).status
        val expectedOrderStatus = when (paymentStatus) {
            PaymentStatus.SUCCESS -> OrderStatus.PAID
            PaymentStatus.FAILED -> OrderStatus.PAYMENT_FAILED
            else -> OrderStatus.PAYING
        }
        assertAll(
            { assertThat(paymentStatus.isFinal).isTrue() },
            { assertThat(orderStatusOf(order.id)).isEqualTo(expectedOrderStatus) },
        )
    }

    @DisplayName("주기 동기화는, 마지막 변경 후 30 초가 지난 진행 중 결제만 처리한다.")
    @Test
    fun syncsOnlyStaleInProgress() {
        // arrange
        val order = placeOrder()
        val info = pay(order.id, PgRequestResult.Accepted(KEY))
        stubDetail(PgQueryResult.Found(tx(status = PgTransactionStatus.SUCCESS)))

        // act
        val fresh = paymentFacade.syncPending()
        val stale = paymentFacade.syncPending(now = ZonedDateTime.now().plusSeconds(31))

        // assert
        assertAll(
            { assertThat(fresh).isZero() },
            { assertThat(stale).isEqualTo(1) },
            { assertThat(paymentOf(info).status).isEqualTo(PaymentStatus.SUCCESS) },
        )
    }

    @DisplayName("콜백을 받을 때, ")
    @Nested
    inner class Callback {
        @DisplayName("거래 키로 결제를 찾아 PG 조회 결과로 확정한다.")
        @Test
        fun syncsByTransactionKey() {
            // arrange
            val order = placeOrder()
            val info = pay(order.id, PgRequestResult.Accepted(KEY))
            stubDetail(PgQueryResult.Found(tx(status = PgTransactionStatus.SUCCESS)))

            // act
            paymentFacade.handleCallback(transactionKey = KEY, pgOrderId = "%06d".format(order.id))

            // assert
            assertThat(paymentOf(info).status).isEqualTo(PaymentStatus.SUCCESS)
        }

        /** 검토 초점 4. tx2 보다 콜백이 먼저 오거나, 타임아웃으로 키를 기록하지 못한 경우다. (설계 5.1 장) */
        @DisplayName("거래 키가 아직 연결되지 않았으면, 주문 ID 로 진행 중 결제를 찾는다.")
        @Test
        fun syncsByOrderId_whenKeyIsNotLinkedYet() {
            // arrange
            val order = placeOrder()
            val info = pay(order.id, PgRequestResult.Unknown)
            stubByOrder(PgQueryResult.Found(listOf(tx(status = PgTransactionStatus.SUCCESS, amount = null))))
            stubDetail(PgQueryResult.Found(tx(status = PgTransactionStatus.SUCCESS)))

            // act
            paymentFacade.handleCallback(transactionKey = KEY, pgOrderId = "%06d".format(order.id), now = afterInFlight())

            // assert
            assertAll(
                { assertThat(paymentOf(info).status).isEqualTo(PaymentStatus.SUCCESS) },
                { verify(paymentGateway).findTransaction(any(), eq(KEY)) },
            )
        }

        /**
         * 검토 초점 9. 진행 중 결제에 이미 다른 키가 있으면 이 콜백은 그 결제의 것이 아니다 — 고아 거래이거나 위조다.
         * 대체 경로로 그 결제를 동기화하면 9.1 장이 약속한 ERROR 로그도 남지 않는다. (2026-10-13 결제 설계 5.1 장)
         */
        @DisplayName("주문의 진행 중 결제에 다른 거래 키가 있으면, 그 결제를 건드리지 않는다.")
        @Test
        fun ignores_whenInProgressPaymentHasAnotherKey() {
            // arrange
            val order = placeOrder()
            val info = pay(order.id, PgRequestResult.Accepted(KEY))

            // act
            paymentFacade.handleCallback(transactionKey = OLD_KEY, pgOrderId = "%06d".format(order.id))

            // assert
            assertAll(
                { assertThat(paymentOf(info).status).isEqualTo(PaymentStatus.PENDING) },
                { verify(paymentGateway, never()).findTransaction(any(), any()) },
                { verify(paymentGateway, never()).findTransactionsByOrder(any(), any()) },
            )
        }

        @DisplayName("어떤 결제와도 이어지지 않으면, 예외 없이 무시한다.")
        @Test
        fun ignores_whenNothingMatches() {
            // act
            paymentFacade.handleCallback(transactionKey = "20251013:TR:nobody", pgOrderId = "not-a-number")

            // assert
            verify(paymentGateway, never()).findTransaction(any(), any())
        }
    }
}
