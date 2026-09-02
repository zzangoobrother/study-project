package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.support.PageQuery
import com.loopers.domain.support.PageResult
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class UserCouponRepositoryImpl(
    private val userCouponJpaRepository: UserCouponJpaRepository,
) : UserCouponRepository {
    override fun save(userCoupon: UserCouponModel): UserCouponModel {
        return userCouponJpaRepository.save(userCoupon)
    }

    override fun findByCouponIdAndUserId(couponId: Long, userId: Long): UserCouponModel? {
        return userCouponJpaRepository.findByCouponIdAndUserIdAndDeletedAtIsNull(
            couponId = couponId,
            userId = userId,
        )
    }

    override fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean {
        return userCouponJpaRepository.existsByUserIdAndCouponIdAndDeletedAtIsNull(userId = userId, couponId = couponId)
    }

    override fun use(couponId: Long, userId: Long, now: ZonedDateTime): Int {
        return userCouponJpaRepository.use(couponId = couponId, userId = userId, now = now)
    }

    /** Pageable 은 이 클래스 안에서만 쓰이고, 도메인 계약은 PageQuery / PageResult 로 유지된다. */
    override fun findAllByUserId(userId: Long, pageQuery: PageQuery): PageResult<UserCouponModel> {
        val content = userCouponJpaRepository.findAllByUserId(
            userId = userId,
            pageable = PageRequest.of(pageQuery.page, pageQuery.size),
        )
        val totalElements = userCouponJpaRepository.countByUserIdAndDeletedAtIsNull(userId)

        return PageResult.of(content = content, pageQuery = pageQuery, totalElements = totalElements)
    }
}
