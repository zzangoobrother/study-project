package com.loopers.interfaces.api.admin

import com.loopers.application.payment.PaymentFacade
import com.loopers.domain.order.OrderItemModel
import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.Quantity
import com.loopers.domain.payment.CardNo
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentCommand
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
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.admin.payment.PaymentAdminV1Dto
import com.loopers.support.auth.AdminAuthInterceptor
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
class PaymentAdminV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userService: UserService,
    private val orderService: OrderService,
    private val paymentFacade: PaymentFacade,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockitoBean
    lateinit var paymentGateway: PaymentGateway

    companion object {
        private const val LOGIN_ID = "loopers01"
        private const val ENDPOINT = "/api-admin/v1/payments"
        private const val ADMIN_ID = "admin"
        private const val ADMIN_PW = "admin1234"
        private const val KEY = "20251013:TR:abc123"
        private const val AMOUNT = 5_000L
    }

    private val responseType = object : ParameterizedTypeReference<ApiResponse<PaymentAdminV1Dto.PaymentResponse>>() {}

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun adminHeaders(): HttpHeaders = HttpHeaders().apply {
        set(AdminAuthInterceptor.HEADER_LDAP_ID, ADMIN_ID)
        set(AdminAuthInterceptor.HEADER_LDAP_PW, ADMIN_PW)
        contentType = MediaType.APPLICATION_JSON
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

    /** PG 가 접수만 한 PENDING 결제를 만든다. 최종 결과는 아직 모른다. */
    private fun pendingPaymentId(orderId: Long): Long {
        whenever(paymentGateway.request(any())).thenReturn(PgRequestResult.Accepted(KEY))
        return paymentFacade.request(
            PaymentCommand.Request(LoginId(LOGIN_ID), orderId, CardType.SAMSUNG, CardNo("1234-5678-9814-1451")),
        ).paymentId
    }

    private fun sync(paymentId: Long, headers: HttpHeaders? = adminHeaders()) =
        testRestTemplate.exchange("$ENDPOINT/$paymentId/sync", HttpMethod.POST, headers?.let { HttpEntity<Any>(it) }, responseType)

    @DisplayName("POST /api-admin/v1/payments/{paymentId}/sync")
    @Nested
    inner class Sync {
        @DisplayName("인증 헤더가 없으면, 401 Unauthorized 를 반환한다.")
        @Test
        fun returnsUnauthorized_whenHeadersAreMissing() {
            // act
            val response = sync(1L, headers = null)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("PG 가 성공이면, 200 과 확정된 SUCCESS 상태를 반환한다.")
        @Test
        fun returnsSuccess_whenPgSucceeded() {
            // arrange
            val paymentId = pendingPaymentId(placeOrder().id)
            whenever(paymentGateway.findTransaction(any(), any())).thenReturn(
                PgQueryResult.Found(
                    PgTransaction(transactionKey = KEY, status = PgTransactionStatus.SUCCESS, reason = null, amount = AMOUNT, orderId = null),
                ),
            )

            // act
            val response = sync(paymentId)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.paymentId).isEqualTo(paymentId) },
                { assertThat(response.body?.data?.status).isEqualTo("SUCCESS") },
                { assertThat(response.body?.data?.transactionKey).isEqualTo(KEY) },
            )
        }

        @DisplayName("존재하지 않는 결제면, 404 Not Found 를 반환한다.")
        @Test
        fun returnsNotFound_whenPaymentDoesNotExist() {
            // act
            val response = sync(999L)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }
}
