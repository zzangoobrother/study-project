package com.loopers.interfaces.api

import com.loopers.application.coupon.CouponFacade
import com.loopers.application.coupon.CouponInfo
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponName
import com.loopers.domain.coupon.DiscountType
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
import com.loopers.domain.user.UserName
import com.loopers.domain.user.UserService
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.interfaces.api.order.OrderV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.ZonedDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userService: UserService,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val couponFacade: CouponFacade,
    private val couponJpaRepository: CouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val LOGIN_ID = "loopers01"
        private const val ENDPOINT = "/api/v1/orders"
    }

    private val detailResponseType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}
    private val listResponseType =
        object : ParameterizedTypeReference<ApiResponse<PageResponse<OrderV1Dto.OrderSummaryResponse>>>() {}

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun signUp(loginId: String = LOGIN_ID) =
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
            ProductModel.create(
                brandId = brand.id,
                name = ProductName(name),
                price = Price(price),
                stock = Stock(stock),
            ),
        )
    }

    private fun headers(loginId: String? = LOGIN_ID) =
        HttpHeaders().apply { loginId?.let { set(ApiHeaders.LOGIN_ID, it) } }

    private fun order(vararg items: Pair<Long, Int>, loginId: String? = LOGIN_ID, userCouponId: Long? = null) =
        testRestTemplate.exchange(
            ENDPOINT,
            HttpMethod.POST,
            HttpEntity(
                OrderV1Dto.PlaceRequest(
                    items = items.map { OrderV1Dto.PlaceRequest.Item(it.first, it.second) },
                    userCouponId = userCouponId,
                ),
                headers(loginId),
            ),
            detailResponseType,
        )

    /**
     * 정책을 저장하고 발급까지 마쳐 CouponInfo 를 돌려준다.
     *
     * 만료된 쿠폰과 정률 쿠폰도 만들 수 있도록 인자를 열어 둔다 — 케이스 표의 "만료된 쿠폰이면" 항목이
     * expiresAt 을 과거로 넘기는 것만으로 만들어진다.
     */
    private fun issueCoupon(
        discountType: DiscountType = DiscountType.FIXED_AMOUNT,
        discountValue: Long = 5_000,
        expiresAt: ZonedDateTime = ZonedDateTime.now().plusDays(30),
        loginId: String = LOGIN_ID,
    ): CouponInfo {
        val policy = couponJpaRepository.save(
            CouponModel.create(
                name = CouponName("테스트 쿠폰"),
                discountType = discountType,
                discountValue = discountValue,
                expiresAt = expiresAt,
            ),
        )
        return couponFacade.issue(LoginId(loginId), policy.id)
    }

    private fun getOrders(query: String = "", loginId: String? = LOGIN_ID) =
        testRestTemplate.exchange(
            "$ENDPOINT$query",
            HttpMethod.GET,
            HttpEntity<Any>(headers(loginId)),
            listResponseType,
        )

    private fun getOrder(orderId: Any, loginId: String? = LOGIN_ID) =
        testRestTemplate.exchange(
            "$ENDPOINT/$orderId",
            HttpMethod.GET,
            HttpEntity<Any>(headers(loginId)),
            detailResponseType,
        )

    private fun stockOf(productId: Long): Long = productRepository.findById(productId)!!.stock.value

    @DisplayName("POST /api/v1/orders")
    @Nested
    inner class Place {
        @DisplayName("주문에 성공하면, 200 과 주문 상세가 반환되고 재고가 줄어든다.")
        @Test
        fun returnsOk_andDecreasesStock() {
            // arrange
            signUp()
            val product = saveProduct(price = 10_000, stock = 10)

            // act
            val response = order(product.id to 3)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.totalPrice).isEqualTo(30_000L) },
                { assertThat(response.body?.data?.items).hasSize(1) },
                { assertThat(response.body?.data?.items?.first()?.productName).isEqualTo("운동화") },
                { assertThat(stockOf(product.id)).isEqualTo(7L) },
            )
        }

        @DisplayName("재고가 부족하면, 409 CONFLICT 를 반환한다.")
        @Test
        fun returnsConflict_whenStockIsInsufficient() {
            // arrange
            signUp()
            val product = saveProduct(stock = 1)

            // act
            val response = order(product.id to 2)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(stockOf(product.id)).isEqualTo(1L) },
            )
        }

        @DisplayName("헤더가 없으면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenHeaderIsMissing() {
            // arrange
            signUp()
            val product = saveProduct()

            // act
            val response = order(product.id to 1, loginId = null)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("수량이 0 이하면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenQuantityIsNotPositive() {
            // arrange
            signUp()
            val product = saveProduct()

            // act
            val response = order(product.id to 0)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("항목이 비어 있으면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenItemsAreEmpty() {
            // arrange
            signUp()

            // act
            val response = order()

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("같은 상품을 두 항목으로 보내면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenProductIdIsDuplicated() {
            // arrange
            signUp()
            val product = saveProduct(stock = 10)

            // act
            val response = order(product.id to 1, product.id to 1)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("존재하지 않는 상품이면, 404 NOT_FOUND 를 반환한다.")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            // arrange
            signUp()

            // act
            val response = order(99999L to 1)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("가입되지 않은 로그인 ID 면, 404 NOT_FOUND 를 반환한다.")
        @Test
        fun returnsNotFound_whenUserDoesNotExist() {
            // arrange
            val product = saveProduct()

            // act
            val response = order(product.id to 1, loginId = "nobody")

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("쿠폰을 적용해 주문하면, 200 과 함께 결제액이 줄어든다.")
        @Test
        fun returnsOk_andReducedPaidAmount() {
            // arrange
            signUp()
            val product = saveProduct(price = 10_000, stock = 10)
            val coupon = issueCoupon(discountValue = 5_000)

            // act
            val response = order(product.id to 2, userCouponId = coupon.id)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.totalPrice).isEqualTo(20_000L) },
                { assertThat(response.body?.data?.discountAmount).isEqualTo(5_000L) },
                { assertThat(response.body?.data?.paidAmount).isEqualTo(15_000L) },
            )
        }

        @DisplayName("쿠폰 없이 주문하면, discountAmount 가 0 이고 paidAmount 가 totalPrice 와 같다.")
        @Test
        fun returnsOk_andPaidAmountEqualsTotalPrice_whenNoCoupon() {
            // arrange
            signUp()
            val product = saveProduct(price = 10_000, stock = 10)

            // act
            val response = order(product.id to 2)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.discountAmount).isEqualTo(0L) },
                { assertThat(response.body?.data?.paidAmount).isEqualTo(response.body?.data?.totalPrice) },
            )
        }

        @DisplayName("남의 쿠폰이면, 404 NOT_FOUND 를 반환한다.")
        @Test
        fun returnsNotFound_whenCouponBelongsToAnotherUser() {
            // arrange
            signUp()
            signUp("loopers02")
            val product = saveProduct(stock = 10)
            val coupon = issueCoupon(loginId = "loopers02")

            // act
            val response = order(product.id to 1, userCouponId = coupon.id)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("이미 사용한 쿠폰이면, 409 CONFLICT 를 반환한다.")
        @Test
        fun returnsConflict_whenCouponAlreadyUsed() {
            // arrange
            signUp()
            val product = saveProduct(stock = 10)
            val coupon = issueCoupon()
            order(product.id to 1, userCouponId = coupon.id)

            // act
            val response = order(product.id to 1, userCouponId = coupon.id)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }

        @DisplayName("만료된 쿠폰이면, 409 CONFLICT 를 반환한다.")
        @Test
        fun returnsConflict_whenCouponExpired() {
            // arrange
            signUp()
            val product = saveProduct(stock = 10)
            val coupon = issueCoupon(expiresAt = ZonedDateTime.now().minusDays(1))

            // act
            val response = order(product.id to 1, userCouponId = coupon.id)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }

        @DisplayName("userCouponId 가 숫자가 아니면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenUserCouponIdIsNotNumeric() {
            // arrange
            signUp()
            val product = saveProduct()
            val body = mapOf(
                "items" to listOf(mapOf("productId" to product.id, "quantity" to 1)),
                "userCouponId" to "abc",
            )

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                HttpEntity(body, headers()),
                detailResponseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @DisplayName("GET /api/v1/orders")
    @Nested
    inner class GetOrders {
        @DisplayName("내 주문이 최근순으로 반환되고 항목은 담기지 않는다.")
        @Test
        fun returnsMyOrders() {
            // arrange
            signUp()
            val product = saveProduct(stock = 10)
            order(product.id to 1)
            order(product.id to 2)

            // act
            val response = getOrders()

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content).hasSize(2) },
                { assertThat(response.body?.data?.totalElements).isEqualTo(2L) },
                { assertThat(response.body?.data?.content?.first()?.itemCount).isEqualTo(1) },
            )
        }

        @DisplayName("응답에 no-store 와 Vary 헤더가 실린다.")
        @Test
        fun setsCacheHeaders() {
            // arrange
            signUp()

            // act
            val response = getOrders()

            // assert
            assertAll(
                { assertThat(response.headers.getFirst("Cache-Control")).isEqualTo("no-store") },
                { assertThat(response.headers.getFirst("Vary")).isEqualTo("X-Loopers-LoginId") },
            )
        }

        @DisplayName("페이징·날짜 파라미터가 잘못되면, 400 BAD_REQUEST 를 반환한다.")
        @ParameterizedTest
        @ValueSource(strings = ["?page=-1", "?size=0", "?size=101", "?page=abc", "?startAt=2026-13-01", "?startAt=2026-02-10&endAt=2026-01-31"])
        fun returnsBadRequest_whenParameterIsInvalid(query: String) {
            // arrange
            signUp()

            // act
            val response = getOrders(query = query)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId}")
    @Nested
    inner class GetOrder {
        @DisplayName("항목까지 채워서 반환된다.")
        @Test
        fun returnsOrderWithItems() {
            // arrange
            signUp()
            val product = saveProduct(price = 10_000, stock = 10)
            val placed = order(product.id to 2).body!!.data!!

            // act
            val response = getOrder(placed.id)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.items).hasSize(1) },
                { assertThat(response.body?.data?.items?.first()?.subtotal).isEqualTo(20_000L) },
            )
        }

        @DisplayName("응답에 no-store 와 Vary 헤더가 실린다.")
        @Test
        fun setsCacheHeaders() {
            // arrange
            signUp()
            val product = saveProduct()
            val placed = order(product.id to 1).body!!.data!!

            // act
            val response = getOrder(placed.id)

            // assert
            assertAll(
                { assertThat(response.headers.getFirst("Cache-Control")).isEqualTo("no-store") },
                { assertThat(response.headers.getFirst("Vary")).isEqualTo("X-Loopers-LoginId") },
            )
        }

        @DisplayName("다른 회원의 주문이면, 404 NOT_FOUND 를 반환한다.")
        @Test
        fun returnsNotFound_whenOrderBelongsToAnotherUser() {
            // arrange
            signUp()
            signUp("loopers02")
            val product = saveProduct(stock = 10)
            val placed = order(product.id to 1).body!!.data!!

            // act
            val response = getOrder(placed.id, loginId = "loopers02")

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("주문 ID 가 숫자가 아니면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenOrderIdIsNotNumeric() {
            // arrange
            signUp()

            // act
            val response = getOrder("abc")

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }
}
