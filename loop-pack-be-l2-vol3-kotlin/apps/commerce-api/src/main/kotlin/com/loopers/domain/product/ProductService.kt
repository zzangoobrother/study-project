package com.loopers.domain.product

import com.loopers.domain.support.PageResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductService(
    private val productRepository: ProductRepository,
) {
    /**
     * 상품이 없을 때 예외를 던지지 않고 null 을 반환한다.
     * 도메인 서비스는 "없다" 는 사실만 전달하고, 그것을 오류로 볼지는 유스케이스가 정한다.
     */
    @Transactional(readOnly = true)
    fun getProduct(id: Long): ProductModel? {
        return productRepository.findById(id)
    }

    /**
     * 조건에 맞는 상품 목록을 조회한다.
     *
     * 조건에 맞는 것이 없어도 오류가 아니다. 빈 목록과 totalElements = 0 을 그대로 반환한다.
     * 브랜드 정보는 이 애그리거트의 것이 아니므로 여기서 채우지 않는다.
     */
    @Transactional(readOnly = true)
    fun getProducts(criteria: ProductCriteria.Search): PageResult<ProductModel> {
        return productRepository.findAll(criteria)
    }

    /**
     * 삭제 여부와 무관하게 상품을 조회한다.
     *
     * getProduct 와 계약이 정반대다. 어드민은 삭제된 리소스도 조회할 수 있어야
     * "없어서 404" 와 "삭제돼서 409" 를 구분할 수 있다.
     */
    @Transactional(readOnly = true)
    fun getProductIncludingDeleted(id: Long): ProductModel? {
        return productRepository.findByIdIncludingDeleted(id)
    }

    /** 삭제 여부와 무관하게 상품 목록을 최신순으로 조회한다. 브랜드 정보는 여기서 채우지 않는다. */
    @Transactional(readOnly = true)
    fun getProductPageIncludingDeleted(criteria: ProductCriteria.AdminSearch): PageResult<ProductModel> {
        return productRepository.findAllIncludingDeleted(criteria)
    }
}
