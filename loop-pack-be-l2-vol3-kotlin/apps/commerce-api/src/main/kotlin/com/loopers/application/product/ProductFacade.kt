package com.loopers.application.product

import com.loopers.application.brand.BrandCache
import com.loopers.application.brand.BrandCacheValue
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
 *
 * 캐시도 같은 경계를 따른다. 상품 캐시에는 brandId 만 두고 브랜드는 따로 캐시해 여기서 합친다 —
 * 브랜드 하나를 고칠 때 그 브랜드 상품의 키를 모두 지우지 않아도 되게 하기 위해서다.
 * 상세는 쓰기 커밋 뒤 키를 지워 바로 반영되고, 목록은 지우지 않아 최대 30 초 늦다. (2026-10-04 상품 캐시 설계 2, 3.3 장)
 */
@Component
class ProductFacade(
    private val productService: ProductService,
    private val brandService: BrandService,
    private val productCache: ProductCache,
    private val brandCache: BrandCache,
) {
    /** 빈 페이지도 캐시한다. 빈 결과는 "없음" 이 아니라 정상 결과다. (2026-10-04 상품 캐시 설계 6.2 장) */
    fun getProducts(criteria: ProductCriteria.Search): PageResult<ProductInfo> {
        val page = productCache.getProductList(criteria)
            ?: ProductListCacheValue.from(productService.getProducts(criteria))
                .also { productCache.putProductList(criteria, it) }
        val brands = loadBrands(page.items.map { it.brandId })

        return PageResult.of(
            content = page.items.map { ProductInfo.of(it, brands[it.brandId]) },
            pageQuery = criteria.pageQuery,
            totalElements = page.totalElements,
        )
    }

    /**
     * "상품이 없음" 을 404 로 볼지 결정하는 것은 유스케이스의 책임이므로 이 계층에서 변환한다.
     * 미등록과 소프트 삭제를 구분하지 않는다.
     *
     * 없는 상품은 캐시하지 않는다. 매번 DB 가 판정한다. (2026-10-04 상품 캐시 설계 4 장)
     */
    fun getProduct(id: Long): ProductInfo {
        val product = productCache.getProduct(id)
            ?: productService.getProduct(id)
                ?.let { ProductCacheValue.from(it) }
                ?.also { productCache.putProduct(it) }
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[productId = $id] 존재하지 않는 상품입니다.",
            )
        val brands = loadBrands(listOf(product.brandId))

        return ProductInfo.of(product, brands[product.brandId])
    }

    /**
     * 캐시에 없는 브랜드만 모아 IN 절 한 번으로 조회한다.
     * 상품이 20건이든 100건이든 Redis 왕복 1 회, DB 조회 많아야 1 회이므로 N+1 이 생기지 않는다.
     *
     * 삭제되었거나 없는 브랜드는 결과 맵에 없고(캐시에도 넣지 않는다), 그 상품의 brand 는 null 이 된다.
     */
    private fun loadBrands(brandIds: List<Long>): Map<Long, BrandInfo> {
        val ids = brandIds.distinct()
        if (ids.isEmpty()) return emptyMap()

        val cached = brandCache.getBrands(ids)
        val missing = ids - cached.keys
        val loaded = if (missing.isEmpty()) {
            emptyList()
        } else {
            brandService.getBrands(missing)
                .map { BrandCacheValue.from(it) }
                .also { brandCache.putBrands(it) }
        }

        return (cached.values + loaded).associate { it.id to BrandInfo.from(it) }
    }
}
