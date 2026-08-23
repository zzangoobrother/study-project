package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable

/**
 * 상품 재고.
 *
 * decrease() 를 두지 않는 것은 LikeCount 와 같은 이유다 — 원자성은 메모리 안의 객체가 표현할 수 없는 성질이다.
 * decrease() 를 만들면 그것을 쓰는 코드가 반드시 "읽고 → 빼고 → 쓰기" 가 되어,
 * 동시 주문 두 건이 같은 재고를 읽고 같은 값을 쓰는 초과 판매로 돌아간다.
 * 실제 차감은 ProductRepository 의 조건부 UPDATE 가 하며, 음수 방지는 그 쿼리의 WHERE 절이 맡는다.
 *
 * 따라서 이 값 객체의 역할은 읽기 측 계약이다 —
 * 조회된 값이 0 이상임을 보장하고, 어떤 경로가 그것을 깨면 조회 시점에 터져서 침묵하지 않게 한다.
 * (설계 문서 5.4 장)
 */
@Embeddable
data class Stock(val value: Long) {
    init {
        if (value < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.")
        }
    }

    override fun toString(): String = value.toString()

    companion object {
        val ZERO = Stock(0)
    }
}
