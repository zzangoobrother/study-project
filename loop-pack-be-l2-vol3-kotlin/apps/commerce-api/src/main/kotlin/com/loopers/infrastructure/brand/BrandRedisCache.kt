package com.loopers.infrastructure.brand

import com.loopers.application.brand.BrandCache
import com.loopers.application.brand.BrandCacheValue
import com.loopers.infrastructure.cache.RedisCacheOperations
import org.springframework.stereotype.Component
import java.time.Duration

/** 목록의 브랜드를 한 번에 가져오도록 MGET 을 쓴다. 상품이 몇 건이든 Redis 왕복은 1 회다. */
@Component
class BrandRedisCache(
    private val operations: RedisCacheOperations,
) : BrandCache {
    override fun getBrands(brandIds: Collection<Long>): Map<Long, BrandCacheValue> {
        if (brandIds.isEmpty()) return emptyMap()
        val ids = brandIds.toList()
        return ids.zip(operations.multiGet(ids.map { brandKey(it) }, BrandCacheValue::class.java))
            .mapNotNull { (id, value) -> value?.let { id to it } }
            .toMap()
    }

    override fun putBrands(values: Collection<BrandCacheValue>) =
        values.forEach { operations.set(brandKey(it.id), it, TTL) }

    override fun evictBrand(brandId: Long) =
        operations.delete(listOf(brandKey(brandId)))

    companion object {
        private val TTL: Duration = Duration.ofMinutes(10)

        fun brandKey(brandId: Long): String = "brand:v1:$brandId"
    }
}
