package com.loopers.domain.coupon

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
import com.loopers.domain.support.PageQuery
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.infrastructure.coupon.UserCouponJpaRepository
import java.time.ZonedDateTime

@SpringBootTest
class CouponServiceIntegrationTest @Autowired constructor(
    private val couponService: CouponService,
    private val couponRepository: CouponRepository,
    private val couponJpaRepository: CouponJpaRepository,
    private val userCouponJpaRepository: UserCouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun savedCoupon(
        type: DiscountType = DiscountType.FIXED,
        value: Long = 5_000,
        expiresAt: ZonedDateTime = ZonedDateTime.now().plusDays(30),
    ): CouponModel = couponJpaRepository.save(
        CouponModel.create(
            name = CouponName("테스트 쿠폰"),
            discountType = type,
            discountValue = value,
            expiresAt = expiresAt,
        ),
    )

    @DisplayName("쿠폰을 발급할 때, ")
    @Nested
    inner class Issue {
        @DisplayName("스냅샷이 채워진 채로 저장된다.")
        @Test
        fun savesWithSnapshot() {
            // arrange
            val coupon = savedCoupon(type = DiscountType.RATE, value = 15)

            // act
            val issued = couponService.issue(userId = 1L, couponId = coupon.id)

            // assert
            assertAll(
                { assertThat(issued.id).isPositive() },
                { assertThat(issued.discountType).isEqualTo(DiscountType.RATE) },
                { assertThat(issued.discountValue).isEqualTo(15) },
            )
        }

        @DisplayName("같은 쿠폰을 두 번 발급하면, CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenIssuedTwice() {
            // arrange
            val coupon = savedCoupon()
            couponService.issue(userId = 1L, couponId = coupon.id)

            // act
            val result = assertThrows<CoreException> { couponService.issue(userId = 1L, couponId = coupon.id) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("존재하지 않는 정책이면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenPolicyMissing() {
            // act
            val result = assertThrows<CoreException> { couponService.issue(userId = 1L, couponId = 99999L) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("쿠폰을 소모할 때, ")
    @Nested
    inner class Use {
        @DisplayName("처음이면 true 이고 두 번째는 false 다.")
        @Test
        fun returnsTrueOnceThenFalse() {
            // arrange
            val issued = couponService.issue(userId = 1L, couponId = savedCoupon().id)

            // act
            val first = couponService.use(couponId = issued.couponId, userId = 1L)
            val second = couponService.use(couponId = issued.couponId, userId = 1L)

            // assert
            assertAll(
                { assertThat(first).isTrue() },
                { assertThat(second).isFalse() },
            )
        }

        @DisplayName("만료된 쿠폰이면, false 다.")
        @Test
        fun returnsFalse_whenExpired() {
            // arrange
            val expired = savedCoupon(expiresAt = ZonedDateTime.now().minusDays(1))
            val issued = couponService.issue(userId = 1L, couponId = expired.id)

            // act
            val result = couponService.use(couponId = issued.couponId, userId = 1L)

            // assert
            assertThat(result).isFalse()
        }
    }

    @DisplayName("쿠폰 목록을 조회할 때, ")
    @Nested
    inner class GetUserCoupons {
        @DisplayName("최근 발급이 앞에 오고 다른 회원의 것은 섞이지 않는다.")
        @Test
        fun returnsOwnCouponsInRecentOrder() {
            // arrange
            val first = savedCoupon()
            val second = savedCoupon()
            couponService.issue(userId = 1L, couponId = first.id)
            val latest = couponService.issue(userId = 1L, couponId = second.id)
            couponService.issue(userId = 2L, couponId = first.id)

            // act
            val result = couponService.getUserCoupons(userId = 1L, pageQuery = PageQuery(page = 0, size = 20))

            // assert
            assertAll(
                { assertThat(result.totalElements).isEqualTo(2L) },
                { assertThat(result.content.first().id).isEqualTo(latest.id) },
            )
        }
    }

    @DisplayName("정책 ID 로 쿠폰을 소모할 때, ")
    @Nested
    inner class UseByCouponId {
        @DisplayName("발급받은 회원이면 소모되고 usedAt 이 채워진다.")
        @Test
        fun consumesCoupon_whenIssuedToUser() {
            // arrange
            val policy = savedCoupon()
            val issued = couponService.issue(userId = 1L, couponId = policy.id)

            // act
            val result = couponService.use(couponId = policy.id, userId = 1L)

            // assert
            assertAll(
                { assertThat(result).isTrue() },
                { assertThat(userCouponJpaRepository.findById(issued.id).get().usedAt).isNotNull() },
            )
        }

        @DisplayName("두 번째 호출은 false 다. 재사용이 막힌다.")
        @Test
        fun returnsFalse_whenUsedTwice() {
            // arrange
            val policy = savedCoupon()
            couponService.issue(userId = 1L, couponId = policy.id)
            couponService.use(couponId = policy.id, userId = 1L)

            // act
            val result = couponService.use(couponId = policy.id, userId = 1L)

            // assert
            assertThat(result).isFalse()
        }

        @DisplayName("남의 쿠폰이면 false 다. 소유권이 WHERE 절에 걸려 있다.")
        @Test
        fun returnsFalse_whenOtherUsersCoupon() {
            // arrange
            val policy = savedCoupon()
            couponService.issue(userId = 1L, couponId = policy.id)

            // act
            val result = couponService.use(couponId = policy.id, userId = 2L)

            // assert
            assertThat(result).isFalse()
        }
    }

    @DisplayName("정책을 관리할 때, ")
    @Nested
    inner class ManagePolicy {
        @DisplayName("등록하면 저장되고 ID 가 부여된다.")
        @Test
        fun registersCoupon() {
            // act
            val result = couponService.register(
                CouponCommand.Register(
                    name = CouponName("신규가입"),
                    discountType = DiscountType.RATE,
                    discountValue = 10,
                    minOrderAmount = 10_000,
                    expiresAt = ZonedDateTime.now().plusDays(30),
                ),
            )

            // assert
            assertAll(
                { assertThat(result.id).isPositive() },
                { assertThat(result.minOrderAmount).isEqualTo(10_000L) },
            )
        }

        @DisplayName("없는 정책을 수정하면 NOT_FOUND 다.")
        @Test
        fun throwsNotFound_whenChangingMissingCoupon() {
            // act
            val result = assertThrows<CoreException> {
                couponService.change(
                    CouponCommand.Change(
                        id = 999_999L,
                        name = CouponName("새 이름"),
                        discountType = DiscountType.FIXED,
                        discountValue = 1_000,
                        minOrderAmount = 0,
                        expiresAt = ZonedDateTime.now().plusDays(30),
                    ),
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 정책을 수정하면 CONFLICT 다.")
        @Test
        fun throwsConflict_whenChangingDeletedCoupon() {
            // arrange
            val policy = savedCoupon()
            couponService.delete(policy.id)

            // act
            val result = assertThrows<CoreException> {
                couponService.change(
                    CouponCommand.Change(
                        id = policy.id,
                        name = CouponName("새 이름"),
                        discountType = DiscountType.FIXED,
                        discountValue = 1_000,
                        minOrderAmount = 0,
                        expiresAt = ZonedDateTime.now().plusDays(30),
                    ),
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("삭제해도 발급된 쿠폰은 살아남는다. 삭제는 회수가 아니다.")
        @Test
        fun keepsIssuedCoupons_whenPolicyDeleted() {
            // arrange
            val policy = savedCoupon()
            couponService.issue(userId = 1L, couponId = policy.id)

            // act
            couponService.delete(policy.id)

            // assert
            assertAll(
                // 정책은 공개 조회에서 사라진다 — 더 이상 발급되지 않는다
                { assertThat(couponRepository.findById(policy.id)).isNull() },
                // 발급분은 그대로 쓸 수 있다 (2026-09-01 설계 문서 5.5 장)
                { assertThat(couponService.use(couponId = policy.id, userId = 1L)).isTrue() },
            )
        }

        @DisplayName("어드민 목록은 삭제된 정책도 포함한다.")
        @Test
        fun includesDeletedCoupons_inAdminList() {
            // arrange
            val alive = savedCoupon()
            val deleted = savedCoupon()
            couponService.delete(deleted.id)

            // act
            val result = couponService.getCouponsIncludingDeleted(PageQuery(page = 0, size = 20))

            // assert
            assertAll(
                { assertThat(result.totalElements).isEqualTo(2L) },
                { assertThat(result.content.map { it.id }).containsExactlyInAnyOrder(alive.id, deleted.id) },
            )
        }
    }
}
