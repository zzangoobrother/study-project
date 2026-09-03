package com.loopers.domain.coupon

import com.loopers.domain.support.PageQuery
import com.loopers.domain.support.PageResult

interface CouponRepository {
    /** 삭제된 정책은 없는 것으로 본다. 없으면 null 이며, 404 로 볼지는 유스케이스가 정한다. */
    fun findById(id: Long): CouponModel?

    fun save(coupon: CouponModel): CouponModel

    /** 어드민 전용. 삭제된 정책도 돌려준다. 삭제됨과 없음을 구분하기 위해서다. */
    fun findByIdIncludingDeleted(id: Long): CouponModel?

    /** 어드민 목록. 최신순 고정이며 삭제된 정책도 포함한다. */
    fun findAllIncludingDeleted(pageQuery: PageQuery): PageResult<CouponModel>
}
