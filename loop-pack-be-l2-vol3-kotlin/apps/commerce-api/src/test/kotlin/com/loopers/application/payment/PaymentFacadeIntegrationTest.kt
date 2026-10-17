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
import com.loopers.domain.payment.PgPaymentRequest
import com.loopers.domain.payment.PgRequestResult
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductName
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserName
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest
class PaymentFacadeIntegrationTest @Autowired constructor(
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
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun signUp(loginId: String = LOGIN_ID): Long {
        userService.signUp(
            UserCommand.SignUp(
                loginId = LoginId(loginId),
                password = RawPassword("Loopers1!"),
                name = UserName("홍길동"),
                birthDate = BirthDate.from("1990-01-01"),
                email = Email("$loginId@loopers.com"),
            ),
        )
        return userService.getUser(LoginId(loginId))!!.id
    }

    /** 상품을 거치지 않는다. 결제는 주문의 paidAmount 만 읽는다. */
    private fun placeOrder(userId: Long, price: Long = 10_000, discount: Long = 0): OrderModel =
        orderService.place(
            userId = userId,
            items = listOf(
                OrderItemModel.create(productId = 1L, productName = ProductName("운동화"), unitPrice = Price(price), quantity = Quantity(1)),
            ),
            discountAmount = Price(discount),
        )

    private fun command(orderId: Long, loginId: String = LOGIN_ID) = PaymentCommand.Request(
        loginId = LoginId(loginId),
        orderId = orderId,
        cardType = CardType.SAMSUNG,
        cardNo = CardNo("1234-5678-9814-1451"),
    )

    private fun orderStatusOf(orderId: Long) = orderService.getOrder(orderId)!!.status

    @DisplayName("결제를 요청할 때, ")
    @Nested
    inner class Request {
        @DisplayName("PG 가 접수하면, 결제는 대기 · 주문은 결제 중이고 주문 금액을 그대로 보낸다.")
        @Test
        fun becomesPending_whenAccepted() {
            // arrange
            val userId = signUp()
            val order = placeOrder(userId, price = 10_000, discount = 3_000)
            whenever(paymentGateway.request(any())).thenReturn(PgRequestResult.Accepted(KEY))

            // act
            val info = paymentFacade.request(command(order.id))

            // assert
            val sent = argumentCaptor<PgPaymentRequest>().also { verify(paymentGateway).request(it.capture()) }.firstValue
            val payment = paymentService.getPayment(info.paymentId)!!
            assertAll(
                { assertThat(info.status).isEqualTo("PENDING") },
                { assertThat(payment.transactionKey).isEqualTo(KEY) },
                { assertThat(orderStatusOf(order.id)).isEqualTo(OrderStatus.PAYING) },
                { assertThat(sent.amount).isEqualTo(Price(7_000)) },
                { assertThat(sent.userId).isEqualTo(userId) },
            )
        }

        @DisplayName("서킷이 열려 보내지 못하면, 결제는 실패 · 주문은 결제 실패이고 바로 다시 결제할 수 있다.")
        @Test
        fun failsImmediately_whenNotSent_andCanRetry() {
            // arrange
            val order = placeOrder(signUp())
            whenever(paymentGateway.request(any())).thenReturn(PgRequestResult.NotSent, PgRequestResult.Accepted(KEY))

            // act
            val first = paymentFacade.request(command(order.id))
            val second = paymentFacade.request(command(order.id))

            // assert
            assertAll(
                { assertThat(first.status).isEqualTo("FAILED") },
                { assertThat(first.reason).isNotBlank() },
                { assertThat(second.status).isEqualTo("PENDING") },
                { assertThat(orderStatusOf(order.id)).isEqualTo(OrderStatus.PAYING) },
            )
        }

        @DisplayName("결과를 모르면, 결제는 요청됨 · 주문은 결제 중으로 남는다.")
        @Test
        fun staysRequested_whenUnknown() {
            // arrange
            val order = placeOrder(signUp())
            whenever(paymentGateway.request(any())).thenReturn(PgRequestResult.Unknown)

            // act
            val info = paymentFacade.request(command(order.id))

            // assert
            assertAll(
                { assertThat(info.status).isEqualTo("REQUESTED") },
                { assertThat(paymentService.getPayment(info.paymentId)!!.transactionKey).isNull() },
                { assertThat(orderStatusOf(order.id)).isEqualTo(OrderStatus.PAYING) },
            )
        }

        /** 검토 초점 5. (2026-10-13 결제 설계 4.3 장) */
        @DisplayName("결제 금액이 0 원이면, PG 를 부르지 않고 바로 결제 완료다.")
        @Test
        fun paysImmediately_whenAmountIsZero() {
            // arrange
            val order = placeOrder(signUp(), price = 10_000, discount = 10_000)

            // act
            val info = paymentFacade.request(command(order.id))

            // assert
            assertAll(
                { assertThat(info.status).isEqualTo("SUCCESS") },
                { assertThat(info.amount).isZero() },
                { assertThat(orderStatusOf(order.id)).isEqualTo(OrderStatus.PAID) },
                { verify(paymentGateway, never()).request(any()) },
            )
        }

        @DisplayName("이미 결제 중이면, 409 CONFLICT 예외가 발생하고 PG 를 부르지 않는다.")
        @Test
        fun throwsConflict_whenAlreadyPaying() {
            // arrange
            val order = placeOrder(signUp())
            whenever(paymentGateway.request(any())).thenReturn(PgRequestResult.Unknown)
            paymentFacade.request(command(order.id))

            // act
            val result = assertThrows<CoreException> { paymentFacade.request(command(order.id)) }

            // assert
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT) },
                { verify(paymentGateway).request(any()) },
            )
        }

        @DisplayName("이미 결제 완료면, 409 CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenPaid() {
            // arrange
            val order = placeOrder(signUp(), price = 10_000, discount = 10_000)
            paymentFacade.request(command(order.id))

            // act
            val result = assertThrows<CoreException> { paymentFacade.request(command(order.id)) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("남의 주문이면, 404 NOT_FOUND 예외가 발생하고 주문 상태가 그대로다.")
        @Test
        fun throwsNotFound_whenOrderBelongsToOther() {
            // arrange
            signUp()
            val order = placeOrder(signUp("other01"))

            // act
            val result = assertThrows<CoreException> { paymentFacade.request(command(order.id)) }

            // assert
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND) },
                { assertThat(orderStatusOf(order.id)).isEqualTo(OrderStatus.PAYMENT_PENDING) },
            )
        }

        @DisplayName("주문이 없으면, 404 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenOrderDoesNotExist() {
            // arrange
            signUp()

            // act
            val result = assertThrows<CoreException> { paymentFacade.request(command(999L)) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("회원이 없으면, 404 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenUserDoesNotExist() {
            // arrange
            val order = placeOrder(signUp())

            // act
            val result = assertThrows<CoreException> { paymentFacade.request(command(order.id, loginId = "nobody01")) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
