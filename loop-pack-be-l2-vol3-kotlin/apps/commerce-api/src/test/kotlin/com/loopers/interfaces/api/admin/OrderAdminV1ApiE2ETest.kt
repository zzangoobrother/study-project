package com.loopers.interfaces.api.admin

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.Stock
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserName
import com.loopers.domain.user.UserRepository
import com.loopers.domain.user.UserService
import com.loopers.interfaces.api.ApiHeaders
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import com.loopers.interfaces.api.admin.order.OrderAdminV1Dto
import com.loopers.interfaces.api.order.OrderV1Dto
import com.loopers.support.auth.AdminAuthInterceptor
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderAdminV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userService: UserService,
    private val userRepository: UserRepository,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT = "/api-admin/v1/orders"
        private const val ORDER_ENDPOINT = "/api/v1/orders"
        private const val ADMIN_ID = "admin"
        private const val ADMIN_PW = "admin1234"
    }

    private val orderType = object : ParameterizedTypeReference<ApiResponse<OrderAdminV1Dto.OrderResponse>>() {}
    private val pageType =
        object : ParameterizedTypeReference<ApiResponse<PageResponse<OrderAdminV1Dto.OrderSummaryResponse>>>() {}
    private val placeType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun adminHeaders(): HttpHeaders = HttpHeaders().apply {
        set(AdminAuthInterceptor.HEADER_LDAP_ID, ADMIN_ID)
        set(AdminAuthInterceptor.HEADER_LDAP_PW, ADMIN_PW)
        contentType = MediaType.APPLICATION_JSON
    }

    private fun signUp(loginId: String): UserModel =
        userService.signUp(
            UserCommand.SignUp(
                loginId = LoginId(loginId),
                password = RawPassword("Loopers1!"),
                name = UserName("홍길동"),
                birthDate = BirthDate.from("1990-01-01"),
                email = Email("$loginId@loopers.com"),
            ),
        )

    private fun saveProduct(name: String = "운동화", price: Long = 10_000, stock: Long = 10): ProductModel {
        val brand = brandRepository.save(BrandModel.create(BrandName("루퍼스")))
        return productRepository.save(
            ProductModel.create(brandId = brand.id, name = ProductName(name), price = Price(price), stock = Stock(stock)),
        )
    }

    /** 공개 주문 API 를 그대로 호출해 실제 주문을 만든다. 재고 차감·스냅샷 저장까지 실제 경로를 거치게 하기 위해서다. */
    private fun place(loginId: String, productId: Long, quantity: Int = 1): Long {
        val headers = HttpHeaders().apply { set(ApiHeaders.LOGIN_ID, loginId) }
        val request = OrderV1Dto.PlaceRequest(listOf(OrderV1Dto.PlaceRequest.Item(productId, quantity)))
        val response = testRestTemplate.exchange(ORDER_ENDPOINT, HttpMethod.POST, HttpEntity(request, headers), placeType)
        return response.body!!.data!!.id
    }

    /**
     * 인터셉터가 /api-admin 하위 경로에 실제로 등록됐는지 확인하는 첫 지점이다.
     * (BrandAdminV1ApiE2ETest.Authentication 과 같은 취지)
     */
    @DisplayName("어드민 API 인증")
    @Nested
    inner class Authentication {
        @DisplayName("목록 조회에 인증 헤더가 없으면, 401 Unauthorized 를 반환한다.")
        @Test
        fun returnsUnauthorized_whenHeadersAreMissing_onList() {
            // act
            val response = testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, null, pageType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("상세 조회에 인증 헤더가 없으면, 401 Unauthorized 를 반환한다.")
        @Test
        fun returnsUnauthorized_whenHeadersAreMissing_onDetail() {
            // act
            val response = testRestTemplate.exchange("$ENDPOINT/1", HttpMethod.GET, null, orderType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @DisplayName("GET /api-admin/v1/orders")
    @Nested
    inner class GetOrders {
        @DisplayName("여러 회원의 주문이 최신순으로 나오고, 각 항목에 loginId 가 채워진다.")
        @Test
        fun ordersByMostRecent_withLoginIdFilled() {
            // arrange
            val first = signUp("loopers01")
            val second = signUp("loopers02")
            val product = saveProduct(stock = 10)
            val firstOrderId = place(first.loginId.value, product.id)
            val secondOrderId = place(second.loginId.value, product.id)

            // act
            val response = testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, HttpEntity<Any>(adminHeaders()), pageType)

            // assert
            val content = response.body?.data?.content
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(content?.map { it.id }).containsExactly(secondOrderId, firstOrderId) },
                { assertThat(content?.map { it.user?.loginId }).containsExactly("loopers02", "loopers01") },
            )
        }

        /**
         * 탈퇴 회원을 결과에서 빼면 "탈퇴한 회원의 주문" 과 "알 수 없는 회원의 주문" 이 둘 다
         * user = null 로 뭉개진다. UserRepository.findAllByIdsIncludingDeleted 를 만든 이유가 이 테스트다.
         */
        @DisplayName("탈퇴한 회원의 주문도 loginId 가 채워진다.")
        @Test
        fun fillsLoginId_evenWhenUserIsSoftDeleted() {
            // arrange
            val user = signUp("loopers01")
            val product = saveProduct(stock = 10)
            val orderId = place(user.loginId.value, product.id)
            user.delete()
            userRepository.save(user)

            // act
            val response = testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, HttpEntity<Any>(adminHeaders()), pageType)

            // assert
            val found = response.body?.data?.content?.single { it.id == orderId }
            assertAll(
                { assertThat(found?.user?.id).isEqualTo(user.id) },
                { assertThat(found?.user?.loginId).isEqualTo("loopers01") },
            )
        }

        /**
         * OrderSummaryResponse 에는 애초에 items 필드가 없다. (공개 OrderV1Dto.OrderSummaryResponse 와 같은 설계)
         * 그래서 여기서 검증할 것은 "필드가 비어 있다" 가 아니라 itemCount 만으로 항목 수를 알 수 있다는 점이다.
         */
        @DisplayName("요약 응답이라 항목은 담기지 않고, itemCount 로만 항목 수를 알 수 있다.")
        @Test
        fun returnsSummaryWithoutItems() {
            // arrange
            val user = signUp("loopers01")
            val product = saveProduct(stock = 10)
            place(user.loginId.value, product.id, quantity = 3)

            // act
            val response = testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, HttpEntity<Any>(adminHeaders()), pageType)

            // assert
            assertThat(response.body?.data?.content?.first()?.itemCount).isEqualTo(1)
        }

        @DisplayName("size 가 상한을 넘으면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenSizeExceedsMax() {
            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT?size=101",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                pageType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("page 가 음수면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenPageIsNegative() {
            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT?page=-1",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                pageType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @DisplayName("GET /api-admin/v1/orders/{orderId}")
    @Nested
    inner class GetOrder {
        @DisplayName("항목이 채워진다.")
        @Test
        fun returnsOrderWithItems() {
            // arrange
            val user = signUp("loopers01")
            val product = saveProduct(name = "운동화", price = 10_000, stock = 10)
            val orderId = place(user.loginId.value, product.id, quantity = 2)

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/$orderId",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                orderType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.items).hasSize(1) },
                { assertThat(response.body?.data?.items?.first()?.productName).isEqualTo("운동화") },
                { assertThat(response.body?.data?.totalPrice).isEqualTo(20_000L) },
                { assertThat(response.body?.data?.user?.loginId).isEqualTo("loopers01") },
            )
        }

        /**
         * 공개 OrderV1Controller.getOrder 는 남의 주문이면 404 로 숨기지만, 이 API 에는 그 판정 기준이
         * 되는 "요청자" 개념 자체가 없다 — 헤더에 loginId 를 싣지 않는다. 그래서 누구의 주문이든 보인다.
         */
        @DisplayName("다른 회원의 주문도 예외 없이 조회된다.")
        @Test
        fun returnsOrder_regardlessOfWhichUserPlacedIt() {
            // arrange
            val other = signUp("loopers02")
            val product = saveProduct(stock = 10)
            val otherOrderId = place(other.loginId.value, product.id)

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/$otherOrderId",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                orderType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.user?.id).isEqualTo(other.id) },
            )
        }

        @DisplayName("존재하지 않는 주문이면, 404 Not Found 를 반환한다.")
        @Test
        fun returnsNotFound_whenOrderDoesNotExist() {
            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/99999",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                orderType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        /** ApiControllerAdvice 가 타입 불일치를 이미 400 으로 바꾸므로 코드 추가 없이 통과해야 한다. */
        @DisplayName("주문 ID 가 숫자가 아니면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenOrderIdIsNotNumeric() {
            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/abc",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                orderType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }
}
