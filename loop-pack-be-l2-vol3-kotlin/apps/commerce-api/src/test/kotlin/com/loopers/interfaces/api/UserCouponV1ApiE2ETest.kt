package com.loopers.interfaces.api

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponName
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.coupon.CouponStatus
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserName
import com.loopers.domain.user.UserService
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.interfaces.api.coupon.CouponV1Dto
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
class UserCouponV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userService: UserService,
    private val couponService: CouponService,
    private val couponJpaRepository: CouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val LOGIN_ID = "loopers1"
    }

    private val issueResponseType = object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.CouponResponse>>() {}
    private val listResponseType =
        object : ParameterizedTypeReference<ApiResponse<PageResponse<CouponV1Dto.CouponResponse>>>() {}

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun signUp(loginId: String = LOGIN_ID): UserModel = userService.signUp(
        UserCommand.SignUp(
            loginId = LoginId(loginId),
            password = RawPassword("Loopers1!"),
            name = UserName("홍길동"),
            birthDate = BirthDate.from("1990-01-01"),
            email = Email("$loginId@loopers.com"),
        ),
    )

    private fun savedCoupon(
        name: String = "테스트 쿠폰",
        expiresAt: ZonedDateTime = ZonedDateTime.now().plusDays(30),
    ): CouponModel = couponJpaRepository.save(
        CouponModel.create(
            name = CouponName(name),
            discountType = DiscountType.FIXED,
            discountValue = 5_000,
            expiresAt = expiresAt,
        ),
    )

    /** 실제 발급 API 를 그대로 태워 목록 조회 대상 상태를 만든다. */
    private fun issue(couponId: Long, loginId: String = LOGIN_ID): CouponV1Dto.CouponResponse? =
        testRestTemplate.exchange(
            "/api/v1/coupons/$couponId/issue",
            HttpMethod.POST,
            HttpEntity<Any>(HttpHeaders().apply { set(ApiHeaders.LOGIN_ID, loginId) }),
            issueResponseType,
        ).body?.data

    /** loginId 가 null 이면 헤더를 아예 넣지 않는다. */
    private fun getUserCoupons(query: String = "", loginId: String? = LOGIN_ID) =
        testRestTemplate.exchange(
            "/api/v1/users/me/coupons$query",
            HttpMethod.GET,
            HttpEntity<Any>(HttpHeaders().apply { loginId?.let { set(ApiHeaders.LOGIN_ID, it) } }),
            listResponseType,
        )

    @DisplayName("GET /api/v1/users/me/coupons")
    @Nested
    inner class GetUserCoupons {
        @DisplayName("발급한 쿠폰이 목록에 200 과 함께 반환된다.")
        @Test
        fun returnsIssuedCoupon() {
            // arrange
            signUp()
            val coupon = savedCoupon(name = "신규가입 5천원")
            issue(coupon.id)

            // act
            val response = getUserCoupons()

            // assert
            val data = response.body?.data
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(data?.content).hasSize(1) },
                { assertThat(data?.content?.first()?.couponId).isEqualTo(coupon.id) },
                { assertThat(data?.content?.first()?.name).isEqualTo("신규가입 5천원") },
                { assertThat(data?.totalElements).isEqualTo(1L) },
            )
        }

        @DisplayName("세 상태(AVAILABLE/USED/EXPIRED)가 각각 반환된다.")
        @Test
        fun returnsEveryStatus() {
            // arrange
            val user = signUp()
            val available = issue(savedCoupon(name = "유효").id)!!
            val expired = issue(savedCoupon(name = "만료", expiresAt = ZonedDateTime.now().minusDays(1)).id)!!
            val toUse = issue(savedCoupon(name = "사용").id)!!
            couponService.use(couponId = toUse.couponId, userId = user.id)

            // act
            val response = getUserCoupons()

            // assert
            val byId = response.body?.data?.content?.associateBy { it.id }
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(byId?.get(available.id)?.status).isEqualTo(CouponStatus.AVAILABLE) },
                { assertThat(byId?.get(expired.id)?.status).isEqualTo(CouponStatus.EXPIRED) },
                { assertThat(byId?.get(toUse.id)?.status).isEqualTo(CouponStatus.USED) },
            )
        }

        @DisplayName("파라미터가 없으면, page 0 size 20 이 적용된다.")
        @Test
        fun appliesDefaults_whenNoParameterIsGiven() {
            // arrange
            signUp()

            // act
            val response = getUserCoupons()

            // assert
            val data = response.body?.data
            assertAll(
                { assertThat(data?.page).isEqualTo(0) },
                { assertThat(data?.size).isEqualTo(20) },
            )
        }

        @DisplayName("쿠폰이 없으면, 빈 목록과 totalElements 0 을 반환한다.")
        @Test
        fun returnsEmptyPage_whenNoCoupon() {
            // arrange
            signUp()

            // act
            val response = getUserCoupons()

            // assert
            val data = response.body?.data
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(data?.content).isEmpty() },
                { assertThat(data?.totalElements).isEqualTo(0L) },
            )
        }

        @DisplayName("헤더가 없으면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenHeaderIsMissing() {
            // arrange
            signUp()

            // act
            val response = getUserCoupons(loginId = null)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("가입되지 않은 로그인 ID 면, 404 NOT_FOUND 를 반환한다.")
        @Test
        fun returnsNotFound_whenUserDoesNotExist() {
            // act
            val response = getUserCoupons(loginId = "nobody")

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("페이징 파라미터가 범위를 벗어나면, 400 BAD_REQUEST 를 반환한다.")
        @ParameterizedTest
        @ValueSource(strings = ["?page=-1", "?size=0", "?size=101", "?page=abc"])
        fun returnsBadRequest_whenPagingParameterIsInvalid(query: String) {
            // arrange
            signUp()

            // act
            val response = getUserCoupons(query = query)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        /**
         * Vary 가 없으면 공유 캐시가 A 의 쿠폰 목록을 B 에게 그대로 돌려줄 수 있다.
         * URL 이 모든 사용자에게 동일하기 때문이다.
         */
        @DisplayName("응답에 no-store 와 Vary 헤더가 실린다.")
        @Test
        fun setsCacheHeaders() {
            // arrange
            signUp()

            // act
            val response = getUserCoupons()

            // assert
            assertAll(
                { assertThat(response.headers.getFirst("Cache-Control")).isEqualTo("no-store") },
                { assertThat(response.headers.getFirst("Vary")).isEqualTo("X-Loopers-LoginId") },
            )
        }
    }
}
