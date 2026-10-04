package com.loopers.application.brand

/**
 * 브랜드 캐시. 상품 상세 · 목록이 응답에 합치는 브랜드 정보를 담는다.
 *
 * 상품 캐시와 나눈 이유는 ProductCacheValue 의 KDoc 에 있다. 장애 처리 계약은 ProductCache 와 같다.
 */
interface BrandCache {
    /** 캐시에 있는 것만 담아 돌려준다. 빠진 ID 는 호출자가 DB 에서 채운다. */
    fun getBrands(brandIds: Collection<Long>): Map<Long, BrandCacheValue>

    fun putBrands(values: Collection<BrandCacheValue>)

    fun evictBrand(brandId: Long)
}
