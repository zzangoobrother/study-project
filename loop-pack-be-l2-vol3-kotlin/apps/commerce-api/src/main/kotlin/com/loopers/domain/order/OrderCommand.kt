package com.loopers.domain.order

import com.loopers.domain.user.LoginId
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

/**
 * 주문 쓰기 유스케이스의 입력.
 *
 * 값 객체만 담으므로 이 객체가 만들어졌다는 것 자체가 포맷 검증 통과를 의미한다.
 * productId 만 원시 타입인 것은 ProductModel 과 같은 이유다 — 상품 ID 라는 개념은 상품 쪽에 속한다.
 */
class OrderCommand {
    data class Place(
        val loginId: LoginId,
        val items: List<Item>,
        val couponId: Long? = null,
    ) {
        init {
            if (items.isEmpty()) {
                throw CoreException(ErrorType.BAD_REQUEST, "주문 항목은 최소 1개 이상이어야 합니다.")
            }
            /**
             * 같은 상품을 두 항목으로 보내면 합산하지 않고 막는다. (설계 문서 6.7 장)
             * 합산하면 재고 검증은 맞아떨어지지만 응답의 항목 수가 요청과 달라져,
             * 클라이언트가 그것이 의도된 동작인지 서버가 무언가를 잃어버린 것인지 알 수 없다.
             */
            if (items.map { it.productId }.distinct().size != items.size) {
                throw CoreException(ErrorType.BAD_REQUEST, "같은 상품을 여러 항목으로 보낼 수 없습니다.")
            }
        }
    }

    data class Item(
        val productId: Long,
        val quantity: Quantity,
    )
}
