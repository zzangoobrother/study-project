package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.Check

/**
 * 상품 엔티티.
 *
 * 브랜드를 객체가 아닌 brandId 로 참조한다. (설계 문서 5.3 장)
 * 애그리거트 경계를 도메인 타입으로 강제하고, 목록 조회에서 N+1 이 생길 경로를 문법적으로 차단한다.
 *
 * stock 과 like_count 의 CHECK 제약은 최후 방어선이다.
 * 음수를 실제로 막는 것은 각 차감 쿼리의 WHERE 절(`stock >= :quantity`, `like_count > 0`)이며
 * 이 제약이 그것을 대신하지 않는다. 이것이 있는 이유는 그 방어선이 사라지는 경우를 위해서다 —
 * WHERE 조건을 빠뜨린 벌크 UPDATE 가 새로 추가되면 애플리케이션은 조용히 통과시키지만 DB 는 거부한다.
 * 코드는 잘못될 수 있어도 저장된 값이 음수가 되는 일만은 없게 한다.
 *
 * 두 컬럼이 같은 제약을 갖는 것은 우연이 아니다. 둘 다 값 객체에 증감 메서드를 두지 않고
 * 원자적 UPDATE 에 맡긴 값이며(설계 문서 5.4 장·6.4 장), 그래서 방어가 SQL 한 줄에 걸려 있다는 약점도 같다.
 *
 * 주의 — 이 제약은 Hibernate 가 DDL 을 생성하는 환경(local·test)에만 적용된다.
 * dev·qa·prd 는 ddl-auto 가 none 이므로 스키마에 ALTER TABLE 을 직접 적용해야 한다.
 */
@Entity
@Table(
    name = "products",
    indexes = [Index(name = "idx_products_brand_id", columnList = "brand_id")],
)
@Check(name = "ck_products_stock_non_negative", constraints = "stock >= 0")
@Check(name = "ck_products_like_count_non_negative", constraints = "like_count >= 0")
class ProductModel private constructor(
    brandId: Long,
    name: ProductName,
    price: Price,
    likeCount: LikeCount,
    stock: Stock,
) : BaseEntity() {
    @Column(name = "brand_id", nullable = false)
    var brandId: Long = brandId
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "name", nullable = false, length = ProductName.MAX_LENGTH))
    var name: ProductName = name
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "price", nullable = false))
    var price: Price = price
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "like_count", nullable = false))
    var likeCount: LikeCount = likeCount
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "stock", nullable = false))
    var stock: Stock = stock
        protected set

    init {
        // brandId 만 값 객체가 아니라 원시 타입이므로(설계 문서 5.2 장) 이 검증만 애그리거트가 직접 한다.
        // 브랜드 ID 라는 개념은 BrandModel 쪽에 속하며, 상품이 그것을 감싸는 타입을 따로 정의하면
        // 같은 식별자에 두 개의 타입이 생긴다.
        if (brandId <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "브랜드 ID 는 양수여야 합니다.")
        }
    }

    /**
     * 이름과 가격을 한 번에 교체한다.
     *
     * 이 시그니처에 brandId 와 likeCount 가 없는 것이 두 요구사항의 이행이다.
     * "상품의 브랜드는 수정할 수 없음" 을 if 문으로 막는 대신 매개변수를 두지 않는 쪽을 택했다.
     * 검증은 잊을 수 있지만 없는 매개변수는 잊을 수 없다.
     *
     * 필드별 메서드로 나누지 않는 이유는 수정 API 가 PUT — 전체 교체 — 이기 때문이다.
     * 값 검증은 ProductName 과 Price 가 이미 소유한다.
     *
     * stock 이 매개변수에 들어온 것은 재고가 상품의 속성이기 때문이다. (설계 문서 5.6 장)
     * PUT 은 전체 교체이므로 재고도 교체 대상이다.
     * 주문에 의한 차감은 이 경로를 타지 않는다 — 그쪽은 조건부 UPDATE 이며 엔티티를 거치지 않는다.
     */
    fun change(name: ProductName, price: Price, stock: Stock) {
        this.name = name
        this.price = price
        this.stock = stock
    }

    companion object {
        /**
         * likeCount 는 기본값 0 이며, 인자로 받는 경로는 로컬 시드 데이터를 위해 열어둔 것이다. (설계 문서 8.1 장)
         * increase() / decrease() 를 여는 것보다 표면이 좁다.
         *
         * stock 도 같은 이유로 기본값 0 이다. 재고 없이 등록하고 나중에 채우는 것이 정상 흐름이다.
         */
        fun create(
            brandId: Long,
            name: ProductName,
            price: Price,
            likeCount: LikeCount = LikeCount.ZERO,
            stock: Stock = Stock.ZERO,
        ): ProductModel = ProductModel(
            brandId = brandId,
            name = name,
            price = price,
            likeCount = likeCount,
            stock = stock,
        )
    }
}
