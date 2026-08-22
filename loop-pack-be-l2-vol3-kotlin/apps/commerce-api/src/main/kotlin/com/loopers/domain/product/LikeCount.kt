package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable

/**
 * 상품의 좋아요 수. 정렬을 위해 상품에 비정규화해 둔 값이다.
 *
 * increase() / decrease() 를 두지 않는 것은 여전히 의도적이며, 이유가 바뀌었다.
 * 예전 이유는 "값을 바꾸는 유스케이스가 아직 없다" 였지만 좋아요 기능이 생겨 그것은 더 이상 참이 아니다.
 *
 * 지금의 이유는 이렇다. 증감은 원자적이어야 하고, 원자성은 메모리 안의 객체가 표현할 수 없는 성질이다.
 * increase() 를 만들면 그것을 쓰는 코드가 반드시 "읽고 → 더하고 → 쓰기" 가 되어,
 * 동시 요청 두 건이 같은 값을 읽고 같은 값을 쓰는 갱신 손실로 돌아간다.
 * 실제 증감은 ProductRepository 의 원자적 UPDATE 가 하며, 음수 방지는 그 쿼리의 WHERE 절이 맡는다.
 *
 * 따라서 이 값 객체의 역할은 런타임 방어에서 읽기 측 계약으로 바뀌었다 —
 * 조회된 값이 0 이상임을 보장하고, 어떤 경로가 그것을 깨면 조회 시점에 터져서 침묵하지 않게 한다.
 * (설계 문서 2026-08-20-product-like-design.md 6.5 장)
 */
@Embeddable
data class LikeCount(val value: Long) {
    init {
        if (value < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "좋아요 수는 0 이상이어야 합니다.")
        }
    }

    override fun toString(): String = value.toString()

    companion object {
        val ZERO = LikeCount(0)
    }
}
