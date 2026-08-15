package com.loopers.domain.product

import com.loopers.domain.support.PageResult

interface ProductRepository {
    /** 로컬 시더가 상품을 한 번에 넣을 때 쓴다. */
    fun saveAll(products: List<ProductModel>): List<ProductModel>

    /** 상품 등록 API 가 쓴다. */
    fun save(product: ProductModel): ProductModel

    /** 소프트 삭제된 상품은 없는 것으로 취급한다. */
    fun findById(id: Long): ProductModel?

    /** 소프트 삭제된 상품은 content 와 totalElements 양쪽에서 제외된다. */
    fun findAll(criteria: ProductCriteria.Search): PageResult<ProductModel>

    /**
     * 삭제 여부와 무관하게 조회한다. 어드민 전용이다.
     *
     * findById 에 플래그를 다는 대신 이름을 나눈 이유는 그 플래그가 상위 계층 시그니처를 전부 오염시키기 때문이다.
     */
    fun findByIdIncludingDeleted(id: Long): ProductModel?

    /** 삭제 여부와 무관하게 최신순으로 페이징 조회한다. 삭제된 상품도 content 와 totalElements 양쪽에 포함된다. */
    fun findAllIncludingDeleted(criteria: ProductCriteria.AdminSearch): PageResult<ProductModel>

    /**
     * 브랜드에 속한 살아 있는 상품을 모두 조회한다. 브랜드 삭제 시 연쇄 삭제 대상을 찾는 용도다.
     *
     * 이 메서드만 삭제를 제외하는 것은 용도가 다르기 때문이다.
     * 이미 삭제된 상품을 다시 삭제 대상으로 끌어올 이유가 없고, 이 성질이 연쇄 삭제의 멱등성을 만든다.
     */
    fun findAllByBrandId(brandId: Long): List<ProductModel>
}
