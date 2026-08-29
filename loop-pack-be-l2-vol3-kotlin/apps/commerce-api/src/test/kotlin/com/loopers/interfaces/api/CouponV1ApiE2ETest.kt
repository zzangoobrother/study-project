package com.loopers.interfaces.api

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponName
import com.loopers.domain.coupon.CouponStatus
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.UserCommand
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
class CouponV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userService: UserService,
    private val couponJpaRepository: CouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val LOGIN_ID = "loopers1"
    }

    private val responseType = object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.CouponResponse>>() {}

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun signUp(loginId: String = LOGIN_ID) = userService.signUp(
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
            discountType = DiscountType.FIXED_AMOUNT,
            discountValue = 5_000,
            expiresAt = expiresAt,
        ),
    )

    /** loginId 가 null 이면 헤더를 아예 넣지 않는다. */
    private fun issue(couponId: Any, loginId: String? = LOGIN_ID) =
        testRestTemplate.exchange(
            "/api/v1/coupons/$couponId/issue",
            HttpMethod.POST,
            HttpEntity<Any>(HttpHeaders().apply { loginId?.let { set(ApiHeaders.LOGIN_ID, it) } }),
            responseType,
        )

    @DisplayName("POST /api/v1/coupons/{couponId}/issue")
    @Nested
    inner class Issue {
        @DisplayName("발급에 성공하면, 200 과 AVAILABLE 쿠폰이 반환된다.")
        @Test
        fun returnsOk_andAvailableCoupon() {
            // arrange
            signUp()
            val coupon = savedCoupon(name = "신규가입 5천원")

            // act
            val response = issue(coupon.id)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.name).isEqualTo("신규가입 5천원") },
                { assertThat(response.body?.data?.status).isEqualTo(CouponStatus.AVAILABLE) },
                { assertThat(response.body?.data?.usedAt).isNull() },
            )
        }

        @DisplayName("이미 발급받았으면, 409 CONFLICT 를 반환한다.")
        @Test
        fun returnsConflict_whenAlreadyIssued() {
            // arrange
            signUp()
            val coupon = savedCoupon()
            issue(coupon.id)

            // act
            val response = issue(coupon.id)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }

        @DisplayName("존재하지 않는 정책이면, 404 NOT_FOUND 를 반환한다.")
        @Test
        fun returnsNotFound_whenPolicyDoesNotExist() {
            // arrange
            signUp()

            // act
            val response = issue(99999L)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("헤더가 없으면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenHeaderIsMissing() {
            // arrange
            signUp()
            val coupon = savedCoupon()

            // act
            val response = issue(coupon.id, loginId = null)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("로그인 ID 형식이 잘못되면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenLoginIdFormatIsInvalid() {
            // arrange
            val coupon = savedCoupon()

            // act
            val response = issue(coupon.id, loginId = "loopers-01")

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("가입되지 않은 로그인 ID 면, 404 NOT_FOUND 를 반환한다.")
        @Test
        fun returnsNotFound_whenUserDoesNotExist() {
            // arrange
            val coupon = savedCoupon()

            // act
            val response = issue(coupon.id, loginId = "nobody")

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("쿠폰 ID 가 숫자가 아니면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenCouponIdIsNotNumeric() {
            // arrange
            signUp()

            // act
            val response = issue("abc")

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }
}
