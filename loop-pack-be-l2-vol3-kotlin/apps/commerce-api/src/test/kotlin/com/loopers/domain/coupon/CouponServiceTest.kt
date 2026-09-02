package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.time.ZonedDateTime

/**
 * CouponService 의 순수 단위 테스트. 저장소 둘을 목으로 대체해 DB 없이 분기와 협력자 호출만 본다.
 *
 * LikeServiceTest 와 같은 이유로 목을 쓴다 — 핵심 단언이 "특정 메서드를 호출하지 않았다" 이며,
 * 손수 만든 페이크로 호출 여부를 추적하려면 그 자체로 테스트 인프라를 새로 심어야 한다.
 */
class CouponServiceTest {
    companion object {
        private const val USER_ID = 1L
        private const val COUPON_ID = 10L
    }

    private val couponRepository = mock<CouponRepository>()
    private val userCouponRepository = mock<UserCouponRepository>()
    private val couponService = CouponService(couponRepository, userCouponRepository)

    private fun policy(): CouponModel =
        CouponModel.create(
            name = CouponName("테스트 쿠폰"),
            discountType = DiscountType.FIXED,
            discountValue = 5_000,
            expiresAt = ZonedDateTime.now().plusDays(30),
        ).apply { ReflectionTestUtils.setField(this, "id", COUPON_ID) }

    @DisplayName("쿠폰을 발급할 때, ")
    @Nested
    inner class Issue {
        @DisplayName("정책이 있고 발급 이력이 없으면, 저장한다.")
        @Test
        fun saves_whenNotIssuedYet() {
            // arrange
            val found = policy()
            whenever(couponRepository.findById(COUPON_ID)).thenReturn(found)
            whenever(userCouponRepository.existsByUserIdAndCouponId(USER_ID, COUPON_ID)).thenReturn(false)
            whenever(userCouponRepository.save(any())).thenAnswer { it.arguments[0] as UserCouponModel }

            // act
            val issued = couponService.issue(userId = USER_ID, couponId = COUPON_ID)

            // assert
            assertAll(
                { assertThat(issued.userId).isEqualTo(USER_ID) },
                { assertThat(issued.couponId).isEqualTo(COUPON_ID) },
                { assertThat(issued.discountValue).isEqualTo(5_000) },
                { verify(userCouponRepository).save(any()) },
            )
        }

        @DisplayName("정책이 없으면, NOT_FOUND 예외를 던지고 저장하지 않는다.")
        @Test
        fun throwsNotFound_whenPolicyDoesNotExist() {
            // arrange
            whenever(couponRepository.findById(COUPON_ID)).thenReturn(null)

            // act
            val result = assertThrows<CoreException> { couponService.issue(userId = USER_ID, couponId = COUPON_ID) }

            // assert
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND) },
                { verify(userCouponRepository, never()).save(any()) },
            )
        }

        @DisplayName("이미 발급받았으면, CONFLICT 예외를 던지고 저장하지 않는다.")
        @Test
        fun throwsConflict_whenAlreadyIssued() {
            // arrange
            val found = policy()
            whenever(couponRepository.findById(COUPON_ID)).thenReturn(found)
            whenever(userCouponRepository.existsByUserIdAndCouponId(USER_ID, COUPON_ID)).thenReturn(true)

            // act
            val result = assertThrows<CoreException> { couponService.issue(userId = USER_ID, couponId = COUPON_ID) }

            // assert
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT) },
                { verify(userCouponRepository, never()).save(any()) },
            )
        }
    }

    @DisplayName("쿠폰을 소모할 때, ")
    @Nested
    inner class Use {
        @DisplayName("영향 행이 1 이면, true 를 반환한다.")
        @Test
        fun returnsTrue_whenOneRowAffected() {
            // arrange
            whenever(userCouponRepository.use(couponId = eq(COUPON_ID), userId = eq(USER_ID), now = any()))
                .thenReturn(1)

            // act
            val result = couponService.use(couponId = COUPON_ID, userId = USER_ID)

            // assert
            assertThat(result).isTrue()
        }

        @DisplayName("영향 행이 0 이면, false 를 반환한다.")
        @Test
        fun returnsFalse_whenNoRowAffected() {
            // arrange
            whenever(userCouponRepository.use(couponId = eq(COUPON_ID), userId = eq(USER_ID), now = any()))
                .thenReturn(0)

            // act
            val result = couponService.use(couponId = COUPON_ID, userId = USER_ID)

            // assert
            assertThat(result).isFalse()
        }

        @DisplayName("선조회 없이 곧바로 저장소의 use 를 호출한다.")
        @Test
        fun doesNotLookUpBeforeUse() {
            // arrange
            // 조회는 파사드가 할인 계산을 위해 따로 한다. 서비스의 use 는 전이만 담당한다. (설계 문서 7.2 장)
            whenever(userCouponRepository.use(couponId = eq(COUPON_ID), userId = eq(USER_ID), now = any()))
                .thenReturn(1)

            // act
            couponService.use(couponId = COUPON_ID, userId = USER_ID)

            // assert
            verify(userCouponRepository, never()).findByCouponIdAndUserId(any(), any())
        }
    }

    @DisplayName("쿠폰을 조회할 때, ")
    @Nested
    inner class GetUserCoupon {
        @DisplayName("저장소에 그대로 위임하고 결과를 반환한다.")
        @Test
        fun delegatesToRepository() {
            // arrange
            val issued = UserCouponModel.issue(userId = USER_ID, coupon = policy())
            whenever(userCouponRepository.findByCouponIdAndUserId(COUPON_ID, USER_ID)).thenReturn(issued)

            // act
            val result = couponService.getUserCoupon(couponId = COUPON_ID, userId = USER_ID)

            // assert
            assertThat(result).isSameAs(issued)
        }

        @DisplayName("없거나 남의 쿠폰이면, 예외 없이 null 을 반환한다.")
        @Test
        fun returnsNull_whenNotFound() {
            // arrange
            // 404 로 볼지는 유스케이스가 정한다. 도메인 서비스는 "없다" 는 사실만 전달한다.
            whenever(userCouponRepository.findByCouponIdAndUserId(COUPON_ID, USER_ID)).thenReturn(null)

            // act
            val result = couponService.getUserCoupon(couponId = COUPON_ID, userId = USER_ID)

            // assert
            assertThat(result).isNull()
        }
    }
}
