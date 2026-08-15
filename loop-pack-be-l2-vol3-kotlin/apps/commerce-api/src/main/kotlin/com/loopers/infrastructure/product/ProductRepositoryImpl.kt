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

    override fun findById(id: Long): ProductModel? {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id)
    }

    override fun findAll(criteria: ProductCriteria.Search): PageResult<ProductModel> {
        return productQueryDslRepository.search(criteria)
    }

    override fun findByIdIncludingDeleted(id: Long): ProductModel? {
        return productJpaRepository.findById(id).orElse(null)
    }

    override fun findAllIncludingDeleted(criteria: ProductCriteria.AdminSearch): PageResult<ProductModel> {
        return productQueryDslRepository.searchIncludingDeleted(criteria)
    }
}
