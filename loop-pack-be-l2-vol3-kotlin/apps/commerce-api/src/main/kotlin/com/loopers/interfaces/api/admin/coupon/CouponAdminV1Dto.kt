package com.loopers.interfaces.api.admin.coupon

import com.loopers.application.admin.coupon.CouponAdminInfo
import com.loopers.domain.coupon.CouponCommand
import com.loopers.domain.coupon.CouponName
import com.loopers.domain.coupon.DiscountType
import java.time.ZonedDateTime

class CouponAdminV1Dto {
    /**
     * 어드민 쿠폰 응답. 목록의 원소와 단건 조회 응답이 같은 타입이다.
     *
     * 필드명이 도메인과 다른 것은 공개 CouponV1Dto 와 같은 이유다 —
     * 요구사항 명세의 와이어 계약을 따르고, 변환은 이 from() 한 곳에만 둔다. (2026-09-01 설계 문서 5.2 장)
     *
     * deleted 를 담는 이유는 어드민 목록이 삭제분을 포함하기 때문이다.
     * 담지 않으면 목록에서 삭제된 정책과 살아 있는 정책을 구분할 수 없다.
     */
    data class CouponResponse(
        val id: Long,
        val name: String,
        val type: DiscountType,
        val value: Long,
        val minOrderAmount: Long,
        val expiredAt: ZonedDateTime,
        val issuedCount: Long,
        val deleted: Boolean,
        val deletedAt: ZonedDateTime?,
        val createdAt: ZonedDateTime,
        val updatedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(info: CouponAdminInfo): CouponResponse = CouponResponse(
                id = info.id,
                name = info.name.value,
                type = info.discountType,
                value = info.discountValue,
                minOrderAmount = info.minOrderAmount,
                expiredAt = info.expiresAt,
                issuedCount = info.issuedCount,
                deleted = info.deleted,
                deletedAt = info.deletedAt,
                createdAt = info.createdAt,
                updatedAt = info.updatedAt,
            )
        }
    }

    /**
     * 정책 등록 요청.
     *
     * minOrderAmount 를 생략하면 0 이다. 명세가 "(선택)" 이라 했고,
     * "조건 없음" 은 "0 원 이상" 과 정확히 같은 뜻이다. (2026-09-01 설계 문서 5.6 장)
     */
    data class RegisterRequest(
        val name: String,
        val type: DiscountType,
        val value: Long,
        val minOrderAmount: Long = 0,
        val expiredAt: ZonedDateTime,
    ) {
        fun toCommand(): CouponCommand.Register = CouponCommand.Register(
            name = CouponName(name),
            discountType = type,
            discountValue = value,
            minOrderAmount = minOrderAmount,
            expiresAt = expiredAt,
        )
    }

    /**
     * 정책 수정 요청. PUT 이므로 전체 교체다.
     *
     * 부분 수정이 아니므로 이름만 고치려는 요청도 전 필드를 보내야 한다.
     * 빠뜨리면 그 필드가 기본값으로 덮인다. (2026-09-01 설계 문서 11.5 장)
     */
    data class ChangeRequest(
        val name: String,
        val type: DiscountType,
        val value: Long,
        val minOrderAmount: Long = 0,
        val expiredAt: ZonedDateTime,
    ) {
        fun toCommand(id: Long): CouponCommand.Change = CouponCommand.Change(
            id = id,
            name = CouponName(name),
            discountType = type,
            discountValue = value,
            minOrderAmount = minOrderAmount,
            expiresAt = expiredAt,
        )
    }
}
