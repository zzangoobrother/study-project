package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponName
import com.loopers.domain.coupon.CouponStatus
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.support.PageQuery
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserName
import com.loopers.domain.user.UserService
import com.loopers.infrastructure.coupon.CouponJpaRepository
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.ZonedDateTime

@SpringBootTest
class CouponFacadeIntegrationTest @Autowired constructor(
    private val couponFacade: CouponFacade,
    private val couponService: CouponService,
    private val couponJpaRepository: CouponJpaRepository,
    private val userService: UserService,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val loginId = LoginId("loopers1")

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun signUp(id: LoginId = loginId) = userService.signUp(
        UserCommand.SignUp(
            loginId = id,
            password = RawPassword("Loopers1!"),
            name = UserName("홍길동"),
            birthDate = BirthDate.from("1990-01-01"),
            email = Email("${id.value}@loopers.com"),
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

    @DisplayName("쿠폰을 발급할 때, ")
    @Nested
    inner class Issue {
        @DisplayName("발급된 쿠폰이 AVAILABLE 상태로 반환된다.")
        @Test
        fun returnsAvailableCoupon() {
            // arrange
            signUp()
            val coupon = savedCoupon(name = "신규가입 5천원")

            // act
            val info = couponFacade.issue(loginId, coupon.id)

            // assert
            assertAll(
                { assertThat(info.name).isEqualTo("신규가입 5천원") },
                { assertThat(info.status).isEqualTo(CouponStatus.AVAILABLE) },
                { assertThat(info.usedAt).isNull() },
            )
        }

        @DisplayName("이미 만료된 정책이면, 발급은 되지만 EXPIRED 상태다.")
        @Test
        fun returnsExpiredCoupon_whenPolicyAlreadyExpired() {
            // arrange
            signUp()
            val coupon = savedCoupon(expiresAt = ZonedDateTime.now().minusDays(1))

            // act
            val info = couponFacade.issue(loginId, coupon.id)

            // assert
            assertThat(info.status).isEqualTo(CouponStatus.EXPIRED)
        }

        @DisplayName("두 번 발급하면, CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenIssuedTwice() {
            // arrange
            signUp()
            val coupon = savedCoupon()
            couponFacade.issue(loginId, coupon.id)

            // act
            val result = assertThrows<CoreException> { couponFacade.issue(loginId, coupon.id) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("존재하지 않는 정책이면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenPolicyMissing() {
            // arrange
            signUp()

            // act
            val result = assertThrows<CoreException> { couponFacade.issue(loginId, 99999L) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("가입되지 않은 로그인 ID 면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenUserMissing() {
            // arrange
            val coupon = savedCoupon()

            // act
            val result = assertThrows<CoreException> { couponFacade.issue(LoginId("nobody"), coupon.id) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("내 쿠폰 목록을 조회할 때, ")
    @Nested
    inner class GetUserCoupons {
        @DisplayName("세 상태가 각각 올바르게 반환된다.")
        @Test
        fun returnsEveryStatus() {
            // arrange
            val user = signUp()
            val available = couponFacade.issue(loginId, savedCoupon(name = "유효").id)
            val expired = couponFacade.issue(loginId, savedCoupon(name = "만료", expiresAt = ZonedDateTime.now().minusDays(1)).id)
            val toUse = couponFacade.issue(loginId, savedCoupon(name = "사용").id)
            couponService.use(couponId = toUse.couponId, userId = user.id)

            // act
            val result = couponFacade.getUserCoupons(loginId, PageQuery(page = 0, size = 20))
            val byId = result.content.associateBy { it.id }

            // assert
            assertAll(
                { assertThat(result.totalElements).isEqualTo(3L) },
                { assertThat(byId[available.id]?.status).isEqualTo(CouponStatus.AVAILABLE) },
                { assertThat(byId[expired.id]?.status).isEqualTo(CouponStatus.EXPIRED) },
                { assertThat(byId[toUse.id]?.status).isEqualTo(CouponStatus.USED) },
            )
        }

        @DisplayName("다른 회원의 쿠폰은 섞이지 않는다.")
        @Test
        fun doesNotMixOtherUsersCoupons() {
            // arrange
            signUp()
            signUp(LoginId("loopers2"))
            couponFacade.issue(loginId, savedCoupon().id)

            // act
            val result = couponFacade.getUserCoupons(LoginId("loopers2"), PageQuery(page = 0, size = 20))

            // assert
            assertAll(
                { assertThat(result.content).isEmpty() },
                { assertThat(result.totalElements).isEqualTo(0L) },
            )
        }

        @DisplayName("쿠폰이 없으면, 빈 목록과 totalElements 0 이 반환된다.")
        @Test
        fun returnsEmpty_whenNoCoupon() {
            // arrange
            signUp()

            // act
            val result = couponFacade.getUserCoupons(loginId, PageQuery(page = 0, size = 20))

            // assert
            assertAll(
                { assertThat(result.content).isEmpty() },
                { assertThat(result.totalElements).isEqualTo(0L) },
            )
        }
    }
}
