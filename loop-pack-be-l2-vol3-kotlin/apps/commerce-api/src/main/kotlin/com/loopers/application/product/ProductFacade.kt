package com.loopers.application.product

import com.loopers.application.brand.BrandInfo
import com.loopers.domain.brand.BrandService
import com.loopers.domain.product.ProductCriteria
import com.loopers.domain.product.ProductService
import com.loopers.domain.support.PageResult
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

/**
 * 상품과 브랜드라는 두 애그리거트를 조합하는 유스케이스.
 *
 * 조인 대신 조합을 택한 근거는 설계 문서 6.2 장에 있다.
 * 도메인 서비스는 각자 자기 애그리거트만 알고, 둘을 합치는 책임은 여기에만 있다.
 */
@Component
class ProductFacade(
    private val productService: ProductService,
    private val brandService: BrandService,
) {
    fun getProducts(criteria: ProductCriteria.Search): PageResult<ProductInfo> {
        val products = productService.getProducts(criteria)
        val brands = loadBrands(products.content.map { it.brandId })

        return products.map { ProductInfo.of(it, brands[it.brandId]) }
    }

    /**
     * "상품이 없음" 을 404 로 볼지 결정하는 것은 유스케이스의 책임이므로 이 계층에서 변환한다.
     * 미등록과 소프트 삭제를 구분하지 않는다.
     */
    fun getProduct(id: Long): ProductInfo {
        val product = productService.getProduct(id)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[productId = $id] 존재하지 않는 상품입니다.",
            )
        val brands = loadBrands(listOf(product.brandId))

        return ProductInfo.of(product, brands[product.brandId])
    }

    /**
     * brandId 를 중복 제거해 IN 절 한 번으로 조회한다.
     * 상품이 20건이든 100건이든 이 호출은 1회이므로 N+1 이 생기지 않는다.
     *
     * 삭제되었거나 없는 브랜드는 결과 맵에 없고, 그 상품의 brand 는 null 이 된다.
     */
    private fun loadBrands(brandIds: List<Long>): Map<Long, BrandInfo> {
        return brandService.getBrands(brandIds.distinct())
            .associate { it.id to BrandInfo.from(it) }
    }
}
