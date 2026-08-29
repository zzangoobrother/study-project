package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Check
import java.time.ZonedDateTime

/**
 * 회원에게 발급된 쿠폰.
 *
 * 회원과 정책을 객체가 아닌 식별자로 참조한다. 할인 조건은 발급 시점에 복사해 갖는다. (설계 문서 5.3 장)
 * 그래야 정책이 바뀌어도 발급 시점에 약속한 할인이 고정되고, 주문이 정책 테이블을 읽지 않아도 된다.
 *
 * 상태 변경 메서드가 없는 것은 의도적이다. 이 애그리거트의 유일한 상태 변화는 usedAt 의 null 에서 시각으로인데,
 * 그것을 엔티티 메서드로 하면 "읽고 → 판단하고 → 쓰기" 사이의 틈에서 같은 쿠폰이 두 번 소모된다.
 * 상태를 바꾸는 경로는 INSERT 하나뿐이고, 사용은 저장소의 조건부 UPDATE 가 담당한다. (설계 문서 5.2 장)
 */
@Entity
@Table(
    name = "user_coupons",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_coupons_user_coupon", columnNames = ["user_id", "coupon_id"]),
    ],
    indexes = [Index(name = "idx_user_coupons_user_id_created_at", columnList = "user_id, created_at")],
)
@Check(name = "ck_user_coupons_discount_value_positive", constraints = "discount_value >= 1")
class UserCouponModel private constructor(
    userId: Long,
    couponId: Long,
    name: CouponName,
    discountType: DiscountType,
    discountValue: Long,
    expiresAt: ZonedDateTime,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(name = "coupon_id", nullable = false)
    var couponId: Long = couponId
        protected set

    /**
     * 이름도 스냅샷이다. 목록 응답이 쿠폰 이름을 내려주는데 여기에 없으면
     * 목록 조회가 정책 테이블을 다시 읽어야 하고, 그러면 스냅샷을 둔 이유가 사라진다.
     */
    @Embedded
    @AttributeOverride(
        name = "value",
        column = Column(name = "name", nullable = false, length = CouponName.MAX_LENGTH),
    )
    var name: CouponName = name
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    var discountType: DiscountType = discountType
        protected set

    @Column(name = "discount_value", nullable = false)
    var discountValue: Long = discountValue
        protected set

    @Column(name = "expires_at", nullable = false)
    var expiresAt: ZonedDateTime = expiresAt
        protected set

    @Column(name = "used_at")
    var usedAt: ZonedDateTime? = null
        protected set

    init {
        if (userId <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "회원 ID 는 양수여야 합니다.")
        }
        if (couponId <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 ID 는 양수여야 합니다.")
        }
        // 스냅샷이 복사되는 순간에도 규칙을 다시 확인한다. 복사 과정의 실수가 조용히 통과하지 않는다.
        CouponModel.validateDiscount(discountType, discountValue)
    }

    /**
     * 표현용 상태. 컬럼이 아니라 두 값에서 계산한다. (설계 문서 5.4 장)
     *
     * usedAt 이 EXPIRED 보다 우선한다 — 만료일이 지난 뒤에 목록을 봐도 실제로 썼던 쿠폰은 USED 로 보여야 한다.
     * 만료 판정이 `>=` 인 것은 저장소의 조건부 UPDATE 가 `expires_at > :now` 이기 때문이다.
     * 두 경계가 어긋나면 목록에서 AVAILABLE 로 보인 쿠폰이 주문에서 409 가 나는 구간이 생긴다.
     */
    fun statusAt(now: ZonedDateTime): CouponStatus = when {
        usedAt != null -> CouponStatus.USED
        !expiresAt.isAfter(now) -> CouponStatus.EXPIRED
        else -> CouponStatus.AVAILABLE
    }

    /** 할인 금액. 스냅샷 두 값으로 계산하므로 정책을 다시 읽지 않는다. */
    fun discountFor(totalPrice: Long): Long = discountType.calculate(discountValue, totalPrice)

    companion object {
        /**
         * 정책에서 스냅샷을 복사해 발급한다.
         *
         * 만료 여부를 확인하지 않는다. 만료된 쿠폰을 발급받는 것은 이상하지만 막을 이유도 없고,
         * 막으면 시더가 심은 만료 쿠폰으로 EXPIRED 를 확인할 수 없다. (설계 문서 9 장)
         */
        fun issue(userId: Long, coupon: CouponModel): UserCouponModel = UserCouponModel(
            userId = userId,
            couponId = coupon.id,
            name = coupon.name,
            discountType = coupon.discountType,
            discountValue = coupon.discountValue,
            expiresAt = coupon.expiresAt,
        )
    }
}
