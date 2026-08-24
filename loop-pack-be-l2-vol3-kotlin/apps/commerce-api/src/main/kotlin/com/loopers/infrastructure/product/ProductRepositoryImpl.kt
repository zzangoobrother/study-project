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

    override fun save(product: ProductModel): ProductModel {
        return productJpaRepository.save(product)
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

    override fun findAllByBrandId(brandId: Long): List<ProductModel> {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId)
    }

    override fun increaseLikeCount(productId: Long): Int {
        return productJpaRepository.increaseLikeCount(productId)
    }

    override fun decreaseLikeCount(productId: Long): Int {
        return productJpaRepository.decreaseLikeCount(productId)
    }

    override fun decreaseStock(productId: Long, quantity: Int): Int {
        return productJpaRepository.decreaseStock(productId = productId, quantity = quantity)
    }

    override fun findAllByIds(ids: List<Long>): List<ProductModel> {
        // IN () 은 문법 오류이고 조회할 대상도 없으므로 쿼리 자체를 보내지 않는다. BrandRepositoryImpl 과 같은 처리다.
        if (ids.isEmpty()) return emptyList()

        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(ids)
    }
}
