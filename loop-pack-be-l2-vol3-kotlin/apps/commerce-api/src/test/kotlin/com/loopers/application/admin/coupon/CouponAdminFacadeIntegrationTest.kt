package com.loopers.application.admin.coupon

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponName
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.support.PageQuery
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.time.ZonedDateTime

@SpringBootTest
class CouponAdminFacadeIntegrationTest @Autowired constructor(
    private val couponAdminFacade: CouponAdminFacade,
    private val couponJpaRepository: CouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockitoSpyBean
    private lateinit var couponService: CouponService

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun savedCoupon(name: String): CouponModel = couponJpaRepository.save(
        CouponModel.create(
            name = CouponName(name),
            discountType = DiscountType.FIXED,
            discountValue = 1_000,
            expiresAt = ZonedDateTime.now().plusDays(30),
        ),
    )

    @DisplayName("어드민이 쿠폰 정책 목록을 조회할 때, ")
    @Nested
    inner class GetCoupons {
        @DisplayName("발급 건수가 함께 채워지고, 발급이 없는 정책은 0 이다.")
        @Test
        fun fillsIssuedCount() {
            // arrange
            val issued = savedCoupon("발급된 정책")
            val untouched = savedCoupon("발급 안 된 정책")
            couponService.issue(userId = 1L, couponId = issued.id)
            couponService.issue(userId = 2L, couponId = issued.id)

            // act
            val result = couponAdminFacade.getCoupons(PageQuery(page = 0, size = 20))

            // assert
            val byId = result.content.associateBy { it.id }
            assertAll(
                { assertThat(result.totalElements).isEqualTo(2L) },
                { assertThat(byId[issued.id]?.issuedCount).isEqualTo(2L) },
                // GROUP BY 는 발급이 0 건인 정책의 행을 돌려주지 않는다. 파사드가 0 으로 채워야 한다.
                { assertThat(byId[untouched.id]?.issuedCount).isEqualTo(0L) },
            )
        }

        /**
         * 정책마다 발급 건수를 세면 페이지 크기만큼 쿼리가 나간다. (2026-09-01 설계 문서 7.3 장)
         * getCoupons 가 집계를 루프 안으로 옮기면 이 검증이 깨진다.
         */
        @DisplayName("정책이 여럿이어도 발급 건수 집계는 1회만 수행된다.")
        @Test
        fun queriesIssuedCountOnlyOnce_regardlessOfCouponCount() {
            // arrange
            val first = savedCoupon("정책 1")
            val second = savedCoupon("정책 2")
            val third = savedCoupon("정책 3")

            // act
            couponAdminFacade.getCoupons(PageQuery(page = 0, size = 20))

            // assert
            // 최신순이므로 나중에 만든 것이 앞이다. 이 순서까지 함께 고정된다.
            verify(couponService, times(1))
                .countIssuedByCouponIds(listOf(third.id, second.id, first.id))
        }
    }
}
