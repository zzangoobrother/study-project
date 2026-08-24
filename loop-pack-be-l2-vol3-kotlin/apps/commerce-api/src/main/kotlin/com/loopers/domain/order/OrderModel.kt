package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.domain.product.Price
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

/**
 * 주문 애그리거트 루트.
 *
 * 이 프로젝트에서 JPA 연관관계 매핑을 쓰는 첫 엔티티다. 기존 규약은 "다른 애그리거트는 식별자로 참조한다" 인데,
 * OrderItem 은 다른 애그리거트가 아니라 이 애그리거트의 내부 구성요소다.
 * 주문 없이 존재할 의미가 없고, 함께 만들어져 함께 저장되며, 주문을 거치지 않고 조회할 유스케이스가 없다.
 * 규약을 어기는 것이 아니라 규약이 적용되지 않는 첫 사례다. (설계 문서 5.2 장)
 *
 * 반대로 userId 는 기존 규약 그대로 식별자다. 회원은 주문과 독립적으로 존재하는 다른 애그리거트다.
 *
 * 주의 — 목록 조회가 항목을 읽지 않기 때문에 지금은 N+1 이 생길 경로가 없다.
 * 목록 응답에 항목을 추가하는 순간 N+1 이 살아나며, 그때는 fetch join 이나 BatchSize 가 필요하다.
 * (설계 문서 11.8 장)
 */
@Entity
@Table(
    name = "orders",
    indexes = [Index(name = "idx_orders_user_id_created_at", columnList = "user_id, created_at")],
)
class OrderModel private constructor(
    userId: Long,
    items: List<OrderItemModel>,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private val orderItems: MutableList<OrderItemModel> = items.toMutableList()

    /** 밖으로는 읽기 전용으로만 낸다. 항목은 주문 생성 시점에 확정되고 이후 바뀌지 않는다. */
    val items: List<OrderItemModel> get() = orderItems.toList()

    /**
     * 총액과 항목 수를 저장하는 이유는 목록 조회가 항목을 읽지 않기 때문이다. (설계 문서 4.2 장)
     * 계산해서 채우려면 목록 조회가 order_items 를 다시 건드려야 하고, 그러면 N+1 을 없앤 이유가 사라진다.
     *
     * like_count 와 성격이 다르다. like_count 는 계속 움직이는 값을 비정규화한 것이라 정합이 깨질 수 있지만,
     * 이 둘은 주문 시점에 확정되고 이후 어떤 경로로도 갱신되지 않으므로 어긋날 자리가 없다.
     */
    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "total_price", nullable = false))
    var totalPrice: Price = Price(items.sumOf { it.subtotal.value })
        protected set

    @Column(name = "item_count", nullable = false)
    var itemCount: Int = items.size
        protected set

    init {
        if (userId <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "회원 ID 는 양수여야 합니다.")
        }
        if (items.isEmpty()) {
            throw CoreException(ErrorType.BAD_REQUEST, "주문 항목은 최소 1개 이상이어야 합니다.")
        }
    }

    companion object {
        /**
         * items 를 그대로 들고 있지 않고 복사하는 이유는, 호출자가 넘긴 리스트를 이후에 바꾸면
         * 주문의 총액·항목 수와 실제 항목이 어긋나기 때문이다.
         */
        fun create(userId: Long, items: List<OrderItemModel>): OrderModel =
            OrderModel(userId = userId, items = items)
    }
}
