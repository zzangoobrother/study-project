package com.loopers.infrastructure.product

import com.loopers.application.product.ProductCache
import com.loopers.application.product.ProductCacheValue
import com.loopers.application.product.ProductListCacheValue
import com.loopers.domain.product.ProductCriteria
import com.loopers.infrastructure.cache.RedisCacheOperations
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 상세는 쓰기 커밋 뒤 지우므로 TTL 은 안전망이다 — 커밋 뒤 삭제로도 막지 못하는 드문 경합과 삭제 실패의 상한.
 * 목록은 지우지 않고 TTL 로만 만료한다. 목록이 30 초 늦어도 된다는 것은 2026-10-04 결정이다.
 * (2026-10-04 상품 캐시 설계 2, 5.2 장)
 */
@Component
class ProductRedisCache(
    private val operations: RedisCacheOperations,
) : ProductCache {
    override fun getProduct(productId: Long): ProductCacheValue? =
        operations.get(productKey(productId), ProductCacheValue::class.java)

    override fun putProduct(value: ProductCacheValue) =
        operations.set(productKey(value.id), value, PRODUCT_TTL)

    override fun getProductList(criteria: ProductCriteria.Search): ProductListCacheValue? =
        operations.get(listKey(criteria), ProductListCacheValue::class.java)

    override fun putProductList(criteria: ProductCriteria.Search, value: ProductListCacheValue) =
        operations.set(listKey(criteria), value, LIST_TTL)

    override fun evictProducts(productIds: Collection<Long>) =
        operations.delete(productIds.map { productKey(it) })

    companion object {
        private val PRODUCT_TTL: Duration = Duration.ofMinutes(10)
        private val LIST_TTL: Duration = Duration.ofSeconds(30)

        // v1 은 값 형식의 버전이다. 롤링 배포 중 옛 · 새 인스턴스가 서로의 JSON 을 읽지 않게 한다. (2026-10-04 상품 캐시 설계 4 장)
        fun productKey(productId: Long): String = "product:v1:$productId"

        /** 정렬은 enum 이름이 아니라 파라미터 표기를 쓴다. 이유는 ProductSortType 의 KDoc 과 같다. */
        fun listKey(criteria: ProductCriteria.Search): String =
            "product:list:v1:${criteria.brandId ?: "all"}:${criteria.sort.parameter}:" +
                "${criteria.pageQuery.page}:${criteria.pageQuery.size}"
    }
}
