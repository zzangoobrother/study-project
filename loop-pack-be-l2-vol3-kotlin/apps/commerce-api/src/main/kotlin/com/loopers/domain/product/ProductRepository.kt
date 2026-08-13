package com.loopers.domain.product

import com.loopers.domain.support.PageResult

interface ProductRepository {
    /**
     * 단건 save 를 두지 않는 것은, 이번 범위에서 상품을 저장하는 유일한 주체가 로컬 시더이기 때문이다.
     * 상품 등록 API 가 생길 때 save 를 추가한다.
     */
    fun saveAll(products: List<ProductModel>): List<ProductModel>

    /** 소프트 삭제된 상품은 없는 것으로 취급한다. */
    fun findById(id: Long): ProductModel?

    /** 소프트 삭제된 상품은 content 와 totalElements 양쪽에서 제외된다. */
    fun findAll(criteria: ProductCriteria.Search): PageResult<ProductModel>
}
