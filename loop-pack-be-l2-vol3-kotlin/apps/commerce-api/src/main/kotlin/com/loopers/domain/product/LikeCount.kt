package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable

/**
 * 상품의 좋아요 수. 정렬을 위해 상품에 비정규화해 둔 값이다.
 *
 * increase() / decrease() 를 두지 않는 것은 의도적이다.
 * 값을 바꾸는 유스케이스가 아직 없고, 좋아요 기능이 붙을 때 정해야 할 것들
 * — 동시 갱신 손실 방지, 중복 좋아요 차단, ProductLike 행과의 정합성 보장 시점 —
 * 이 그 메서드의 모양을 결정하기 때문이다. 지금 만들면 반드시 다시 짜게 된다.
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
