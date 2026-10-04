package com.loopers.application.product

import com.loopers.domain.product.ProductCriteria

/**
 * 상품 상세 · 목록 캐시.
 *
 * 구현은 Redis 장애를 삼킨다 — 조회 실패는 null(미스)로, 저장 · 삭제 실패는 로그로 끝난다.
 * 캐시는 응답을 실패시키지 않으므로 호출부는 예외를 처리하지 않는다. (2026-10-04 상품 캐시 설계 7.1 장)
 *
 * application 에 두는 이유: 캐시는 도메인 규칙이 아니라 유스케이스의 성능 장치다.
 * domain 에 두면 도메인이 캐시의 존재를 알게 된다. (2026-10-04 상품 캐시 설계 3.2 장)
 */
interface ProductCache {
    fun getProduct(productId: Long): ProductCacheValue?

    fun putProduct(value: ProductCacheValue)

    fun getProductList(criteria: ProductCriteria.Search): ProductListCacheValue?

    fun putProductList(criteria: ProductCriteria.Search, value: ProductListCacheValue)

    fun evictProducts(productIds: Collection<Long>)
}
