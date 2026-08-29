package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable

/**
 * 쿠폰 이름.
 *
 * 상한을 두는 이유는 ProductName 과 같다 — 컬럼 길이를 코드가 알고 있어야
 * DB 가 자르기 전에 애플리케이션이 400 으로 막을 수 있다.
 */
@Embeddable
data class CouponName(val value: String) {
    init {
        if (value.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 비어 있을 수 없습니다.")
        }
        if (value.length > MAX_LENGTH) {
            throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 $MAX_LENGTH 자 이하여야 합니다.")
        }
    }

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 100
    }
}
