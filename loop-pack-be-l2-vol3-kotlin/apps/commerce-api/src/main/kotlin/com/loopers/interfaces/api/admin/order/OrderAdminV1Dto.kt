package com.loopers.interfaces.api.admin.order

import com.loopers.application.admin.order.OrderAdminInfo
import java.time.ZonedDateTime

class OrderAdminV1Dto {
    /**
     * 어드민 주문 상세 응답.
     *
     * user 가 nullable 인 이유는 OrderAdminInfo.user 와 같다. 주문은 회원을 식별자로만 참조하고
     * FK 가 없어, 회원 행이 사라진 주문이 이론상 가능하다.
     */
    data class OrderResponse(
        val id: Long,
        val user: UserResponse?,
        val totalPrice: Long,
        val itemCount: Int,
        val orderedAt: ZonedDateTime,
        val items: List<Item>,
    ) {
        data class Item(
            val productId: Long,
            val productName: String,
            val unitPrice: Long,
            val quantity: Int,
            val subtotal: Long,
        )

        companion object {
            fun from(info: OrderAdminInfo): OrderResponse = OrderResponse(
                id = info.id,
                user = info.user?.let { UserResponse(id = it.id, loginId = it.loginId) },
                totalPrice = info.totalPrice,
                itemCount = info.itemCount,
                orderedAt = info.orderedAt,
                items = info.items.map {
                    Item(
                        productId = it.productId,
                        productName = it.productName,
                        unitPrice = it.unitPrice,
                        quantity = it.quantity,
                        subtotal = it.subtotal,
                    )
                },
            )
        }
    }

    /**
     * 어드민 주문 목록의 원소. 항목을 담지 않는다. (설계 문서 4.2 장, 공개 OrderV1Dto.OrderSummaryResponse 와 같은 이유)
     *
     * OrderResponse 와 타입을 나누는 이유도 공개 API 와 같다. 같은 타입에서 items 를 빈 배열로
     * 내면 클라이언트가 "항목이 없는 주문" 과 "목록이라 항목을 안 준 것" 을 구분할 수 없다.
     */
    data class OrderSummaryResponse(
        val id: Long,
        val user: UserResponse?,
        val totalPrice: Long,
        val itemCount: Int,
        val orderedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(info: OrderAdminInfo): OrderSummaryResponse = OrderSummaryResponse(
                id = info.id,
                user = info.user?.let { UserResponse(id = it.id, loginId = it.loginId) },
                totalPrice = info.totalPrice,
                itemCount = info.itemCount,
                orderedAt = info.orderedAt,
            )
        }
    }

    /**
     * 노출하는 회원 정보는 id 와 loginId 뿐이다. 이름·이메일·생년월일 같은 개인정보는 담지 않는다.
     * OrderResponse 와 OrderSummaryResponse 가 이 타입을 함께 쓴다 — 목록과 상세에서 회원을 표현하는
     * 방식이 다를 이유가 없기 때문이다.
     */
    data class UserResponse(
        val id: Long,
        val loginId: String,
    )
}
