package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductName
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/**
 * 주문 항목. 주문 시점의 상품 정보를 복사해 갖는다.
 *
 * productName 과 unitPrice 가 상품을 참조하지 않고 값으로 들어와 있는 것이 스냅샷의 실체다.
 * 이 둘이 고정되어야 상품이 이후 이름을 바꾸거나 가격을 올리거나 삭제되어도 주문서가 그대로 읽힌다.
 *
 * productId 를 함께 남기는 목적은 다르다 — "이 주문이 어떤 상품이었나" 를 추적하는 용도이며,
 * 이 값으로 상품을 조회해 응답을 채우지는 않는다. 그렇게 하면 스냅샷을 둔 이유가 사라진다.
 * (설계 문서 5.3 장)
 *
 * order 의 FK 를 이 쪽이 소유하는 이유는 성능이다. 원래는 OrderModel 이 단방향 @OneToMany +
 * @JoinColumn 으로 소유했는데, 그러면 Hibernate 가 이 행을 order_id 없이 INSERT 한 뒤
 * UPDATE order_items SET order_id 를 한 번 더 날린다 — 자식이 부모를 모르는 채로 저장을 시작하기
 * 때문이다. 부하 테스트에서 이 낭비 쿼리가 45만 회 나가며 products 배타 락 보유 시간을 늘리고
 * 있었다. FK 소유자를 자식으로 옮기면 INSERT 문에 order_id 가 바로 실려 그 UPDATE 가 사라진다.
 * (2026-09-06 부하 테스트)
 */
@Entity
@Table(name = "order_items")
class OrderItemModel private constructor(
    productId: Long,
    productName: ProductName,
    unitPrice: Price,
    quantity: Quantity,
) : BaseEntity() {
    @Column(name = "product_id", nullable = false)
    var productId: Long = productId
        protected set

    @Embedded
    @AttributeOverride(
        name = "value",
        column = Column(name = "product_name", nullable = false, length = ProductName.MAX_LENGTH),
    )
    var productName: ProductName = productName
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "unit_price", nullable = false))
    var unitPrice: Price = unitPrice
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "quantity", nullable = false))
    var quantity: Quantity = quantity
        protected set

    /**
     * FK 소유자. lateinit 인 이유는 OrderItemModel.create 시점에는 아직 속할 주문이 없고,
     * OrderModel.create 가 애그리거트를 조립하는 순간에야 정해지기 때문이다 — BaseEntity.createdAt 과
     * 같은 결이다. 세터를 protected 로 막고 [assignOrder] 하나로만 채우는 이유는, 항목이 생성 이후
     * 다른 주문으로 옮겨갈 유스케이스가 없어서다 — 이 연관은 조립 시점에 한 번만 맺힌다.
     *
     * data class 로 만들지 않는 이유도 이 필드 때문이다. toString/equals/hashCode 가 자동 생성되면
     * order 를 되짚어 참조하다 order.items 와 서로를 무한히 되풀이하며 재귀에 빠질 수 있다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    lateinit var order: OrderModel
        protected set

    /**
     * 소계는 저장하지 않고 계산한다.
     * 단가와 수량이 이미 불변으로 고정돼 있어 두 값과 어긋날 수 없고, 컬럼을 늘리면 어긋날 여지만 생긴다.
     */
    val subtotal: Price get() = Price(unitPrice.value * quantity.value)

    init {
        if (productId <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "상품 ID 는 양수여야 합니다.")
        }
    }

    /** OrderModel.create 안에서만 부른다. 외부에 공개된 세터를 대신하는 연관관계 편의 메서드다. */
    internal fun assignOrder(order: OrderModel) {
        this.order = order
    }

    companion object {
        fun create(
            productId: Long,
            productName: ProductName,
            unitPrice: Price,
            quantity: Quantity,
        ): OrderItemModel = OrderItemModel(
            productId = productId,
            productName = productName,
            unitPrice = unitPrice,
            quantity = quantity,
        )
    }
}
