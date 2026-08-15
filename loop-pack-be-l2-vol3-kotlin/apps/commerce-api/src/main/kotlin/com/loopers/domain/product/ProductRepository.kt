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

    /**
     * 삭제 여부와 무관하게 조회한다. 어드민 전용이다.
     *
     * findById 에 플래그를 다는 대신 이름을 나눈 이유는 그 플래그가 상위 계층 시그니처를 전부 오염시키기 때문이다.
     */
    fun findByIdIncludingDeleted(id: Long): ProductModel?

    /** 삭제 여부와 무관하게 최신순으로 페이징 조회한다. 삭제된 상품도 content 와 totalElements 양쪽에 포함된다. */
    fun findAllIncludingDeleted(criteria: ProductCriteria.AdminSearch): PageResult<ProductModel>
}
