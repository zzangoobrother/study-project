package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderInfo
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.Quantity
import com.loopers.domain.user.LoginId
import java.time.ZonedDateTime

class OrderV1Dto {
    /**
     * 주문 요청.
     *
     * quantity 를 Int 로 받고 Quantity 로 감싸는 것만으로 "1 이상" 검증이 수행된다.
     * 위반 시 Quantity 생성자가 CoreException(BAD_REQUEST) 를 던지므로 별도 검증 코드를 두지 않는다.
     */
    data class PlaceRequest(
        val items: List<Item>,
    ) {
        data class Item(
            val productId: Long,
            val quantity: Int,
        )

        fun toCommand(loginId: LoginId): OrderCommand.Place = OrderCommand.Place(
            loginId = loginId,
            items = items.map { OrderCommand.Item(productId = it.productId, quantity = Quantity(it.quantity)) },
        )
    }

    /**
     * 주문 상세 응답. 주문 생성 응답과 같은 타입이다.
     *
     * items 의 productName 과 unitPrice 가 주문 시점의 값이다.
     * 지금 상품을 조회해 채운 값이 아니므로, 상품이 이후 바뀌거나 삭제돼도 이 응답은 그대로다.
     */
    data class OrderResponse(
        val id: Long,
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
            fun from(info: OrderInfo): OrderResponse = OrderResponse(
                id = info.id,
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
     * 주문 목록의 원소. 항목을 담지 않는다. (설계 문서 4.2 장)
     *
     * OrderResponse 와 타입을 나누는 이유는, 같은 타입을 쓰면서 items 를 빈 배열로 내면
     * 클라이언트가 "항목이 없는 주문" 과 "목록이라 항목을 안 준 것" 을 구분할 수 없기 때문이다.
     */
    data class OrderSummaryResponse(
        val id: Long,
        val totalPrice: Long,
        val itemCount: Int,
        val orderedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(info: OrderInfo): OrderSummaryResponse = OrderSummaryResponse(
                id = info.id,
                totalPrice = info.totalPrice,
                itemCount = info.itemCount,
                orderedAt = info.orderedAt,
            )
        }
    }
}
