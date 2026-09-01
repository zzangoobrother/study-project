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
import jakarta.persistence.Table
import org.hibernate.annotations.Check
import java.time.ZonedDateTime

/**
 * 쿠폰 정책. 발급의 원본이다.
 *
 * 읽기 전용에 가깝다 — 시더가 만들고 발급이 읽을 뿐, 갱신하는 경로가 없다.
 * 발급 수량 제한이 없으므로 발급이 이 행을 건드리지도 않는다. (설계 문서 5.1 장)
 *
 * 주의 — CHECK 제약은 Hibernate 가 DDL 을 생성하는 환경(local·test)에만 적용된다.
 * dev 이상은 ddl-auto 가 none 이므로 스키마에 직접 적용해야 한다.
 */
@Entity
@Table(name = "coupons")
@Check(name = "ck_coupons_discount_value_positive", constraints = "discount_value >= 1")
@Check(name = "ck_coupons_min_order_amount_non_negative", constraints = "min_order_amount >= 0")
class CouponModel private constructor(
    name: CouponName,
    discountType: DiscountType,
    discountValue: Long,
    minOrderAmount: Long,
    expiresAt: ZonedDateTime,
) : BaseEntity() {
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

    /**
     * 이 금액 이상일 때만 쓸 수 있다. 0 은 조건 없음을 뜻한다.
     *
     * 할인의 하한 조건일 뿐 상한이 아니다. 정률 100 퍼센트 쿠폰에 이 값을 걸어도
     * 결제액이 0 원이 되는 것을 막지 못한다. (2026-09-01 설계 문서 11.3 장)
     */
    @Column(name = "min_order_amount", nullable = false)
    var minOrderAmount: Long = minOrderAmount
        protected set

    @Column(name = "expires_at", nullable = false)
    var expiresAt: ZonedDateTime = expiresAt
        protected set

    init {
        validateDiscount(discountType, discountValue)
        validateMinOrderAmount(minOrderAmount)
    }

    companion object {
        const val MAX_PERCENTAGE = 100L

        fun create(
            name: CouponName,
            discountType: DiscountType,
            discountValue: Long,
            minOrderAmount: Long = 0,
            expiresAt: ZonedDateTime,
        ): CouponModel = CouponModel(
            name = name,
            discountType = discountType,
            discountValue = discountValue,
            minOrderAmount = minOrderAmount,
            expiresAt = expiresAt,
        )

        /**
         * discountValue 의 유효 범위가 discountType 에 따라 달라 단일 값으로 판정할 수 없다.
         * 두 필드를 함께 봐야 하는 규칙이라 값 객체가 아니라 애그리거트가 소유한다. (설계 문서 5.6 장)
         * UserCouponModel 이 같은 규칙을 다시 확인하므로 여기에 두고 공유한다.
         */
        fun validateDiscount(discountType: DiscountType, discountValue: Long) {
            when (discountType) {
                DiscountType.FIXED ->
                    if (discountValue < 1) {
                        throw CoreException(ErrorType.BAD_REQUEST, "정액 할인은 1 원 이상이어야 합니다.")
                    }

                DiscountType.RATE ->
                    if (discountValue < 1 || discountValue > MAX_PERCENTAGE) {
                        throw CoreException(ErrorType.BAD_REQUEST, "정률 할인은 1 이상 $MAX_PERCENTAGE 이하여야 합니다.")
                    }
            }
        }

        /**
         * UserCouponModel 이 같은 규칙을 다시 확인하므로 여기에 두고 공유한다.
         * validateDiscount 와 나누는 이유는 이 규칙이 discountType 에 의존하지 않기 때문이다.
         */
        fun validateMinOrderAmount(minOrderAmount: Long) {
            if (minOrderAmount < 0) {
                throw CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 0 원 이상이어야 합니다.")
            }
        }
    }
}
