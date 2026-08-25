package com.loopers.domain.product

/**
 * 상품 쓰기 유스케이스의 입력.
 *
 * 값 객체만 담으므로 이 객체가 만들어졌다는 것 자체가 포맷 검증 통과를 의미한다.
 * brandId 만 원시 타입인 것은 ProductModel 과 같은 이유다 — 브랜드 ID 라는 개념은 브랜드 쪽에 속하며,
 * 상품이 그것을 감싸는 타입을 따로 정의하면 같은 식별자에 두 개의 타입이 생긴다.
 *
 * Change 에 brandId 가 없는 것은 "상품의 브랜드는 수정할 수 없음" 요구사항 때문이다.
 *
 * stock 이 Register 와 Change 양쪽에 있는 이유는 재고가 상품의 속성이기 때문이다. (설계 문서 5.6 장)
 * 재고 전용 엔드포인트를 따로 두지 않으므로 등록과 수정이 이 값을 함께 다룬다.
 */
class ProductCommand {
    data class Register(
        val brandId: Long,
        val name: ProductName,
        val price: Price,
        val stock: Stock,
    )

    data class Change(
        val id: Long,
        val name: ProductName,
        val price: Price,
        val stock: Stock,
    )
}
