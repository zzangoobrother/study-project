package com.loopers.domain.coupon

import com.loopers.domain.support.PageQuery
import com.loopers.domain.support.PageResult
import java.time.ZonedDateTime

/**
 * 발급된 쿠폰 저장소.
 *
 * use 가 엔티티를 받지 않고 식별자와 시각을 받는 것이 이 인터페이스의 핵심이다.
 * 엔티티를 읽어 usedAt 을 채우면 읽기와 쓰기 사이의 틈에서 같은 쿠폰이 두 번 소모되므로,
 * 그 연산은 조건을 WHERE 절에 담은 단일 UPDATE 여야 한다. (설계 문서 6.2 장)
 * 그래서 반환이 Unit 이 아니라 영향 행 수이며, 그 숫자가 소모 여부의 유일한 근거다.
 */
interface UserCouponRepository {
    fun save(userCoupon: UserCouponModel): UserCouponModel

    /** 소유자까지 함께 건다. 남의 쿠폰은 없는 것과 같다. (설계 문서 4.3 장) */
    fun findByIdAndUserId(id: Long, userId: Long): UserCouponModel?

    /** 1인 1매 판정용. 최종 방어선은 유니크 제약이다. */
    fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean

    /**
     * 쿠폰을 소모한다. 이미 썼거나 만료됐거나 남의 것이면 아무것도 바꾸지 않는다.
     * 반환값은 영향 행 수다.
     */
    fun use(id: Long, userId: Long, now: ZonedDateTime): Int

    /** 최근 발급 순으로 페이징 조회한다. 상태와 무관하게 전부 반환한다. */
    fun findAllByUserId(userId: Long, pageQuery: PageQuery): PageResult<UserCouponModel>
}
