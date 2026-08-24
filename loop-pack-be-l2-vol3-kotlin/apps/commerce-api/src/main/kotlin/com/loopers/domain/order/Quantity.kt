package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable

/**
 * 주문 수량. 1 이상이다.
 *
 * Price 가 0 을 허용하는 것과 대비된다. 0 원 상품(사은품·증정품)은 실재하지만,
 * 0 개를 사는 주문은 항목이 있으면서 아무것도 사지 않는 상태라 의미가 없다.
 * 허용하면 총액 0 원짜리 빈 주문이 만들어진다. (설계 문서 5.4 장)
 */
@Embeddable
data class Quantity(val value: Int) {
    init {
        if (value < 1) {
            throw CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.")
        }
    }

    override fun toString(): String = value.toString()
}
