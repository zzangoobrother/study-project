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
     * 소계는 저장하지 않고 계산한다.
     * 단가와 수량이 이미 불변으로 고정돼 있어 두 값과 어긋날 수 없고, 컬럼을 늘리면 어긋날 여지만 생긴다.
     */
    val subtotal: Price get() = Price(unitPrice.value * quantity.value)

    init {
        if (productId <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "상품 ID 는 양수여야 합니다.")
        }
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
