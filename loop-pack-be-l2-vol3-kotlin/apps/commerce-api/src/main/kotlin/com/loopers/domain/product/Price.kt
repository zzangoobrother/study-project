package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable

/**
 * 상품 가격. 원 단위 정수다.
 *
 * 0 을 허용한다. 사은품·증정품이 0원으로 등록되는 경우가 실제로 있어 막아야 하는 것은 음수뿐이다.
 */
@Embeddable
data class Price(val value: Long) {
    init {
        if (value < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.")
        }
    }

    override fun toString(): String = value.toString()
}
