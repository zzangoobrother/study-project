package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductCriteria
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.support.PageResult
import org.springframework.stereotype.Component

@Component
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
    private val productQueryDslRepository: ProductQueryDslRepository,
) : ProductRepository {
    override fun saveAll(products: List<ProductModel>): List<ProductModel> {
        return productJpaRepository.saveAll(products)
    }

    // 도메인 계약은 deletedAt 이라는 영속화 세부사항을 몰라도 되도록, 이름을 findById 로 좁혀 노출한다.
    override fun findById(id: Long): ProductModel? {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id)
    }

    override fun findAll(criteria: ProductCriteria.Search): PageResult<ProductModel> {
        return productQueryDslRepository.search(criteria)
    }
}
