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
class CouponModel private constructor(
    name: CouponName,
    discountType: DiscountType,
    discountValue: Long,
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

    @Column(name = "expires_at", nullable = false)
    var expiresAt: ZonedDateTime = expiresAt
        protected set

    init {
        validateDiscount(discountType, discountValue)
    }

    companion object {
        const val MAX_PERCENTAGE = 100L

        fun create(
            name: CouponName,
            discountType: DiscountType,
            discountValue: Long,
            expiresAt: ZonedDateTime,
        ): CouponModel = CouponModel(
            name = name,
            discountType = discountType,
            discountValue = discountValue,
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
    }
}
