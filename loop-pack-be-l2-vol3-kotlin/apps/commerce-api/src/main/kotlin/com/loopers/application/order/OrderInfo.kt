package com.loopers.application.order

import com.loopers.domain.order.OrderModel
import java.time.ZonedDateTime

/**
 * 주문 계층 밖으로 전달되는 정보.
 *
 * 값 객체가 아니라 원시 타입으로 펼쳐서 내보내는 이유는, 이 타입을 소비하는 곳이
 * 컨트롤러 하나뿐이고 거기서 다시 값 객체를 풀어야 하기 때문이다.
 * items 가 목록 조회에서 빈 배열인 것은 목록 조회가 항목을 읽지 않기 때문이다. (설계 문서 4.2 장)
 */
data class OrderInfo(
    val id: Long,
    val totalPrice: Long,
    val discountAmount: Long,
    val paidAmount: Long,
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
        /** 항목까지 채운다. 상세 조회와 주문 생성 응답이 쓴다. */
        fun of(model: OrderModel): OrderInfo = OrderInfo(
            id = model.id,
            totalPrice = model.totalPrice.value,
            discountAmount = model.discountAmount.value,
            paidAmount = model.paidAmount.value,
            itemCount = model.itemCount,
            orderedAt = model.createdAt,
            items = model.items.map {
                Item(
                    productId = it.productId,
                    productName = it.productName.value,
                    unitPrice = it.unitPrice.value,
                    quantity = it.quantity.value,
                    subtotal = it.subtotal.value,
                )
            },
        )

        /**
         * 항목을 비워서 만든다. 목록 조회가 쓴다.
         * 여기서 model.items 를 건드리면 목록 조회가 주문 수만큼 추가 쿼리를 날린다 — N+1 이다.
         */
        fun summaryOf(model: OrderModel): OrderInfo = OrderInfo(
            id = model.id,
            totalPrice = model.totalPrice.value,
            discountAmount = model.discountAmount.value,
            paidAmount = model.paidAmount.value,
            itemCount = model.itemCount,
            orderedAt = model.createdAt,
            items = emptyList(),
        )
    }
}
