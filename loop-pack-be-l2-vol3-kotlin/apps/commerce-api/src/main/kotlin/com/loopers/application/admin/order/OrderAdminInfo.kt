package com.loopers.application.admin.order

import com.loopers.domain.order.OrderModel
import com.loopers.domain.user.UserModel
import java.time.ZonedDateTime

/**
 * 어드민 계층 밖으로 전달되는 주문 정보.
 *
 * 공개 OrderInfo 를 재사용하거나 확장하지 않고 따로 두는 이유는 이 타입이 회원 정보를 담기 때문이다.
 * 회원 정보는 공개 API 응답에 들어가면 안 되는 값이므로, 타입을 분리해 그 값이 공개 경로로
 * 새어 나갈 여지 자체를 없앤다.
 */
data class OrderAdminInfo(
    val id: Long,
    val user: User?,
    val totalPrice: Long,
    val itemCount: Int,
    val orderedAt: ZonedDateTime,
    val items: List<Item>,
) {
    /**
     * 노출하는 회원 정보는 id 와 loginId 뿐이다. 이름·이메일·생년월일 같은 개인정보는 담지 않는다.
     * LoginId 값 객체가 아니라 원시 문자열로 펼쳐 내보내는 이유는 OrderInfo 의 다른 필드들과 같다 —
     * 이 타입을 소비하는 곳이 DTO 하나뿐이고, 거기서 다시 값 객체를 풀어야 하기 때문이다.
     */
    data class User(
        val id: Long,
        val loginId: String,
    ) {
        companion object {
            fun from(model: UserModel): User = User(id = model.id, loginId = model.loginId.value)
        }
    }

    data class Item(
        val productId: Long,
        val productName: String,
        val unitPrice: Long,
        val quantity: Int,
        val subtotal: Long,
    )

    companion object {
        /**
         * 항목까지 채운다. 상세 조회가 쓴다.
         *
         * user 가 nullable 인 이유는 ProductAdminInfo.brand 와 같다. 어드민은 탈퇴한 회원도
         * getUsersIncludingDeleted 로 채우므로, null 은 정말로 회원 행이 사라진 경우뿐이다 —
         * 주문이 userId 로 회원을 식별자로만 참조하고 FK 가 없어 이론상 가능하다.
         */
        fun of(model: OrderModel, user: User?): OrderAdminInfo = OrderAdminInfo(
            id = model.id,
            user = user,
            totalPrice = model.totalPrice.value,
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
         * (OrderInfo.summaryOf 와 같은 경고다)
         */
        fun summaryOf(model: OrderModel, user: User?): OrderAdminInfo = OrderAdminInfo(
            id = model.id,
            user = user,
            totalPrice = model.totalPrice.value,
            itemCount = model.itemCount,
            orderedAt = model.createdAt,
            items = emptyList(),
        )
    }
}
