package com.loopers.interfaces.api

import com.loopers.domain.order.OrderItemModel
import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.Quantity
import com.loopers.domain.payment.PaymentGateway
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
import com.loopers.interfaces.api.order.OrderV1Dto
import com.loopers.interfaces.api.payment.PaymentV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userService: UserService,
    private val orderService: OrderService,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockitoBean
    lateinit var paymentGateway: PaymentGateway

    companion object {
        private const val LOGIN_ID = "loopers01"
        private const val ENDPOINT = "/api/v1/payments"
        private const val KEY = "20251013:TR:abc123"
    }

    private val paymentResponseType = object : ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {}
    private val orderResponseType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}

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

    private fun placeOrder(userId: Long): OrderModel = orderService.place(
        userId = userId,
        items = listOf(
            OrderItemModel.create(productId = 1L, productName = ProductName("운동화"), unitPrice = Price(5_000), quantity = Quantity(1)),
        ),
    )

    private fun headers(loginId: String? = LOGIN_ID) = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        loginId?.let { set(ApiHeaders.LOGIN_ID, it) }
    }

    /** JSON 문자열로 보낸다 — 잘못된 카드사처럼 DTO 로 만들 수 없는 요청도 같은 경로로 보내기 위해서다. */
    private fun pay(body: String, loginId: String? = LOGIN_ID) =
        testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, HttpEntity(body, headers(loginId)), paymentResponseType)

    private fun payBody(orderId: Long, cardType: String = "SAMSUNG", cardNo: String = "1234-5678-9814-1451") =
        """{"orderId":$orderId,"cardType":"$cardType","cardNo":"$cardNo"}"""

    private fun getOrder(orderId: Long) = testRestTemplate.exchange(
        "/api/v1/orders/$orderId",
        HttpMethod.GET,
        HttpEntity<Any>(headers()),
        orderResponseType,
    )

    @DisplayName("POST /api/v1/payments")
    @Nested
    inner class Request {
        @DisplayName("PG 가 접수하면, 200 과 PENDING 이 반환되고 주문은 결제 중이 된다.")
        @Test
        fun returnsPending_whenAccepted() {
            // arrange
            val order = placeOrder(signUp())
            whenever(paymentGateway.request(any())).thenReturn(PgRequestResult.Accepted(KEY))

            // act
            val response = pay(payBody(order.id))

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.status).isEqualTo("PENDING") },
                { assertThat(response.body?.data?.amount).isEqualTo(5_000L) },
                { assertThat(getOrder(order.id).body?.data?.status).isEqualTo("PAYING") },
            )
        }

        @DisplayName("PG 결과를 모르면, 200 과 REQUESTED 가 반환된다.")
        @Test
        fun returnsRequested_whenUnknown() {
            // arrange
            val order = placeOrder(signUp())
            whenever(paymentGateway.request(any())).thenReturn(PgRequestResult.Unknown)

            // act
            val response = pay(payBody(order.id))

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.status).isEqualTo("REQUESTED") },
            )
        }

        @DisplayName("카드 번호 형식이 틀리면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenCardNoIsInvalid() {
            // arrange
            val order = placeOrder(signUp())

            // act
            val response = pay(payBody(order.id, cardNo = "1234567898141451"))

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("카드사가 목록에 없으면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenCardTypeIsUnknown() {
            // arrange
            val order = placeOrder(signUp())

            // act
            val response = pay(payBody(order.id, cardType = "VISA"))

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("로그인 ID 헤더가 없으면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenLoginIdHeaderIsMissing() {
            // arrange
            val order = placeOrder(signUp())

            // act
            val response = pay(payBody(order.id), loginId = null)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("남의 주문이면, 404 NOT_FOUND 를 반환한다.")
        @Test
        fun returnsNotFound_whenOrderBelongsToOther() {
            // arrange
            signUp()
            val order = placeOrder(signUp("other01"))

            // act
            val response = pay(payBody(order.id))

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("이미 결제 중이면, 409 CONFLICT 를 반환한다.")
        @Test
        fun returnsConflict_whenAlreadyPaying() {
            // arrange
            val order = placeOrder(signUp())
            whenever(paymentGateway.request(any())).thenReturn(PgRequestResult.Accepted(KEY))
            pay(payBody(order.id))

            // act
            val response = pay(payBody(order.id))

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }
    }

    private fun callback(orderId: Long, status: String) = testRestTemplate.exchange(
        "$ENDPOINT/callback",
        HttpMethod.POST,
        HttpEntity(
            """{"transactionKey":"$KEY","orderId":"${"%06d".format(orderId)}","cardType":"SAMSUNG",
               "cardNo":"1234-5678-9814-1451","amount":5000,"status":"$status","reason":null}""",
            headers(loginId = null),
        ),
        object : ParameterizedTypeReference<ApiResponse<Any>>() {},
    )

    @DisplayName("POST /api/v1/payments/callback")
    @Nested
    inner class Callback {
        @DisplayName("PG 조회 결과가 성공이면, 200 을 반환하고 주문이 결제 완료가 된다.")
        @Test
        fun paysOrder_whenPgConfirmsSuccess() {
            // arrange
            val order = placeOrder(signUp())
            whenever(paymentGateway.request(any())).thenReturn(PgRequestResult.Accepted(KEY))
            pay(payBody(order.id))
            whenever(paymentGateway.findTransaction(any(), any()))
                .thenReturn(PgQueryResult.Found(PgTransaction(KEY, PgTransactionStatus.SUCCESS, "정상 승인되었습니다.", 5_000L)))

            // act
            val response = callback(order.id, status = "SUCCESS")

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(getOrder(order.id).body?.data?.status).isEqualTo("PAID") },
            )
        }

        /**
         * 콜백은 인증이 없다. 본문의 status 를 믿으면 결제 없이 주문을 PAID 로 만들 수 있다. (2026-10-13 결제 설계 5.1 장)
         */
        @DisplayName("본문이 SUCCESS 여도 PG 조회 결과가 처리 중이면, 200 을 반환하고 주문 상태가 바뀌지 않는다.")
        @Test
        fun ignoresBodyStatus_whenForged() {
            // arrange
            val order = placeOrder(signUp())
            whenever(paymentGateway.request(any())).thenReturn(PgRequestResult.Accepted(KEY))
            pay(payBody(order.id))
            whenever(paymentGateway.findTransaction(any(), any()))
                .thenReturn(PgQueryResult.Found(PgTransaction(KEY, PgTransactionStatus.PENDING, null, 5_000L)))

            // act
            val response = callback(order.id, status = "SUCCESS")

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(getOrder(order.id).body?.data?.status).isEqualTo("PAYING") },
            )
        }
    }
}
