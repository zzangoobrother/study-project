package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.test.util.ReflectionTestUtils
import java.time.ZonedDateTime

class UserCouponModelTest {
    private val now: ZonedDateTime = ZonedDateTime.now()

    private fun coupon(expiresAt: ZonedDateTime): CouponModel =
        CouponModel.create(
            name = CouponName("테스트 쿠폰"),
            discountType = DiscountType.FIXED_AMOUNT,
            discountValue = 5_000,
            expiresAt = expiresAt,
        )

    /**
     * 정책의 id 는 영속화 전이라 0 이다. issue 가 couponId 를 양수로 요구하므로 리플렉션으로 심는다.
     * OrderFacadeTest 가 같은 이유로 쓰는 방식이다.
     */
    private fun couponWithId(id: Long, expiresAt: ZonedDateTime): CouponModel =
        coupon(expiresAt).apply { ReflectionTestUtils.setField(this, "id", id) }

    /** usedAt 의 setter 가 protected 라 리플렉션으로 심는다. 사용은 저장소의 조건부 UPDATE 가 한다. */
    private fun used(model: UserCouponModel, at: ZonedDateTime): UserCouponModel =
        model.apply { ReflectionTestUtils.setField(this, "usedAt", at) }

    @DisplayName("쿠폰을 발급할 때, ")
    @Nested
    inner class Issue {
        @DisplayName("정책의 할인 조건이 스냅샷으로 복사된다.")
        @Test
        fun copiesDiscountSnapshot() {
            // arrange
            val expiresAt = now.plusDays(30)
            val policy = couponWithId(id = 10L, expiresAt = expiresAt)

            // act
            val issued = UserCouponModel.issue(userId = 1L, coupon = policy)

            // assert
            assertAll(
                { assertThat(issued.userId).isEqualTo(1L) },
                { assertThat(issued.couponId).isEqualTo(10L) },
                { assertThat(issued.name).isEqualTo(CouponName("테스트 쿠폰")) },
                { assertThat(issued.discountType).isEqualTo(DiscountType.FIXED_AMOUNT) },
                { assertThat(issued.discountValue).isEqualTo(5_000) },
                { assertThat(issued.expiresAt).isEqualTo(expiresAt) },
                { assertThat(issued.usedAt).isNull() },
            )
        }

        @DisplayName("이미 만료된 정책도 발급된다.")
        @Test
        fun issues_evenWhenPolicyIsAlreadyExpired() {
            // arrange
            // 발급은 만료 여부를 막지 않는다. 만료된 쿠폰은 목록에 EXPIRED 로 보이고 주문에서 409 가 된다.
            val policy = couponWithId(id = 10L, expiresAt = now.minusDays(1))

            // act
            val issued = UserCouponModel.issue(userId = 1L, coupon = policy)

            // assert
            assertThat(issued.statusAt(now)).isEqualTo(CouponStatus.EXPIRED)
        }

        @DisplayName("회원 ID 가 양수가 아니면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = [0L, -1L])
        fun throwsBadRequest_whenUserIdIsNotPositive(userId: Long) {
            // arrange
            val policy = couponWithId(id = 10L, expiresAt = now.plusDays(30))

            // act
            val result = assertThrows<CoreException> { UserCouponModel.issue(userId = userId, coupon = policy) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("정책 ID 가 양수가 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenCouponIdIsNotPositive() {
            // arrange
            // 영속화하지 않은 정책은 id 가 0 이다.
            val policy = coupon(expiresAt = now.plusDays(30))

            // act
            val result = assertThrows<CoreException> { UserCouponModel.issue(userId = 1L, coupon = policy) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("쿠폰의 상태를 판정할 때, ")
    @Nested
    inner class StatusAt {
        private fun issued(expiresAt: ZonedDateTime): UserCouponModel =
            UserCouponModel.issue(userId = 1L, coupon = couponWithId(id = 10L, expiresAt = expiresAt))

        @DisplayName("쓰지 않았고 만료도 아니면, AVAILABLE 이다.")
        @Test
        fun returnsAvailable_whenUnusedAndNotExpired() {
            // act
            val status = issued(now.plusDays(1)).statusAt(now)

            // assert
            assertThat(status).isEqualTo(CouponStatus.AVAILABLE)
        }

        @DisplayName("쓰지 않았지만 만료됐으면, EXPIRED 다.")
        @Test
        fun returnsExpired_whenUnusedAndExpired() {
            // act
            val status = issued(now.minusSeconds(1)).statusAt(now)

            // assert
            assertThat(status).isEqualTo(CouponStatus.EXPIRED)
        }

        @DisplayName("썼고 만료 전이면, USED 다.")
        @Test
        fun returnsUsed_whenUsedAndNotExpired() {
            // arrange
            val model = used(issued(now.plusDays(1)), at = now.minusHours(1))

            // act & assert
            assertThat(model.statusAt(now)).isEqualTo(CouponStatus.USED)
        }

        @DisplayName("썼고 그 뒤에 만료됐으면, EXPIRED 가 아니라 USED 다.")
        @Test
        fun returnsUsed_whenUsedBeforeExpiry() {
            // arrange
            // usedAt 이 EXPIRED 보다 우선한다. "쓴 적 있음" 은 사실이고 "만료됨" 은 시점에 따라 달라지는
            // 판정이라, 사실이 먼저다. (설계 문서 5.4 장)
            val model = used(issued(now.minusDays(1)), at = now.minusDays(2))

            // act & assert
            assertThat(model.statusAt(now)).isEqualTo(CouponStatus.USED)
        }

        @DisplayName("만료 시각과 정확히 같은 순간이면, EXPIRED 다.")
        @Test
        fun returnsExpired_whenExactlyAtExpiry() {
            // arrange
            // 조건부 UPDATE 의 expires_at > :now 와 같은 경계여야 한다. 목록과 주문의 판정이 어긋나면 안 된다.
            val expiresAt = now.plusDays(1)

            // act
            val status = issued(expiresAt).statusAt(expiresAt)

            // assert
            assertThat(status).isEqualTo(CouponStatus.EXPIRED)
        }
    }
}
