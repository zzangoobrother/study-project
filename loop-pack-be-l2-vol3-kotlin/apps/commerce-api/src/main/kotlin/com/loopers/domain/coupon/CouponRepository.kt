package com.loopers.domain.coupon

interface CouponRepository {
    /** 삭제된 정책은 없는 것으로 본다. 없으면 null 이며, 404 로 볼지는 유스케이스가 정한다. */
    fun findById(id: Long): CouponModel?
}
