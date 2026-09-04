package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponModel
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface CouponJpaRepository : JpaRepository<CouponModel, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): CouponModel?

    /**
     * 어드민 목록. 삭제된 정책도 포함한다.
     *
     * id DESC 보조 정렬은 같은 시각의 행이 여럿일 때 페이지 경계에서 중복과 누락을 막는다.
     */
    @Query("SELECT c FROM CouponModel c ORDER BY c.createdAt DESC, c.id DESC")
    fun findAllIncludingDeleted(pageable: Pageable): List<CouponModel>
}
