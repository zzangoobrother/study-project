package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.UserCouponModel
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.ZonedDateTime

interface UserCouponJpaRepository : JpaRepository<UserCouponModel, Long> {
    fun findByCouponIdAndUserIdAndDeletedAtIsNull(couponId: Long, userId: Long): UserCouponModel?

    fun existsByUserIdAndCouponIdAndDeletedAtIsNull(userId: Long, couponId: Long): Boolean

    /**
     * 쿠폰 소모. 판정과 전이가 한 문장 안에서 끝난다.
     *
     * 두 요청이 동시에 이 문을 실행해도 행 잠금이 직렬화하므로, 나중에 도착한 쪽은
     * usedAt IS NULL 을 만족하지 못해 0 행을 받는다. 이것이 재사용 불가의 실체다. (2026-09-01 설계 문서 6.1 장)
     *
     * 조회 키가 발급 ID 가 아니라 정책 ID 인데도 대상이 최대 한 행인 이유는
     * uk_user_coupons_user_coupon (user_id, coupon_id) 유니크 제약 때문이다. (2026-09-01 설계 문서 6.2 장)
     * 이 제약이 사라지면 이 문장은 여러 행을 한꺼번에 소모시킨다.
     *
     * userId 조건이 WHERE 절에 함께 있는 것이 소유권 검증이다. 애플리케이션이 앞서 확인하지만,
     * 확인과 갱신 사이의 틈을 이 조건이 막는다.
     *
     * expiresAt > :now 는 UserCouponModel.statusAt 의 만료 경계와 같아야 한다.
     * 어긋나면 목록에서 AVAILABLE 로 보인 쿠폰이 주문에서 409 가 나는 구간이 생긴다.
     *
     * minOrderAmount 가 이 WHERE 절에 없는 것은 의도적이다. 그 조건은 경합하지 않으므로
     * 애플리케이션이 판정한다. 여기에 넣으면 0 행의 뜻이 셋으로 늘어 진단만 잃는다. (2026-09-01 설계 문서 6.3 장)
     *
     * clearAutomatically 를 켜는 이유는 직전 선조회로 1차 캐시에 올라온 엔티티가 이 UPDATE 를
     * 반영하지 못한 채 남기 때문이다. flushAutomatically 는 반대 방향의 보호다.
     * updatedAt 을 SET 절에 직접 쓰는 이유는 JPQL 벌크 연산이 PreUpdate 콜백을 타지 않기 때문이다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE UserCouponModel c
           SET c.usedAt = :now, c.updatedAt = :now
         WHERE c.couponId = :couponId
           AND c.userId = :userId
           AND c.usedAt IS NULL
           AND c.expiresAt > :now
           AND c.deletedAt IS NULL
        """,
    )
    fun use(
        @Param("couponId") couponId: Long,
        @Param("userId") userId: Long,
        @Param("now") now: ZonedDateTime,
    ): Int

    /**
     * 최근 발급 순이다. id DESC 보조 정렬은 같은 시각의 행이 여럿일 때
     * 페이지 경계에서 중복과 누락을 막는다.
     */
    @Query(
        """
        SELECT c FROM UserCouponModel c
         WHERE c.userId = :userId AND c.deletedAt IS NULL
         ORDER BY c.createdAt DESC, c.id DESC
        """,
    )
    fun findAllByUserId(@Param("userId") userId: Long, pageable: Pageable): List<UserCouponModel>

    fun countByUserIdAndDeletedAtIsNull(userId: Long): Long
}
