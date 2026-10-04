package com.loopers.application.product

import com.loopers.domain.product.ProductModel
import com.loopers.domain.support.PageResult

/** 목록 한 페이지의 캐시 값. page · size 는 키에 들어 있으므로 담지 않는다. */
data class ProductListCacheValue(
    val items: List<ProductCacheValue>,
    val totalElements: Long,
) {
    companion object {
        fun from(page: PageResult<ProductModel>): ProductListCacheValue =
            ProductListCacheValue(
                items = page.content.map { ProductCacheValue.from(it) },
                totalElements = page.totalElements,
            )
    }
}
