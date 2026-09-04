package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.support.PageQuery
import com.loopers.domain.support.PageResult
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

@Component
class CouponRepositoryImpl(
    private val couponJpaRepository: CouponJpaRepository,
) : CouponRepository {
    override fun findById(id: Long): CouponModel? {
        return couponJpaRepository.findByIdAndDeletedAtIsNull(id)
    }

    override fun save(coupon: CouponModel): CouponModel {
        return couponJpaRepository.save(coupon)
    }

    override fun findByIdIncludingDeleted(id: Long): CouponModel? {
        return couponJpaRepository.findById(id).orElse(null)
    }

    /** Pageable 은 이 클래스 안에서만 쓰이고, 도메인 계약은 PageQuery / PageResult 로 유지된다. */
    override fun findAllIncludingDeleted(pageQuery: PageQuery): PageResult<CouponModel> {
        val content = couponJpaRepository.findAllIncludingDeleted(
            pageable = PageRequest.of(pageQuery.page, pageQuery.size),
        )

        // count() 는 삭제분을 포함해 센다. content 와 같은 모집단이라 어긋나지 않는다.
        return PageResult.of(content = content, pageQuery = pageQuery, totalElements = couponJpaRepository.count())
    }
}
