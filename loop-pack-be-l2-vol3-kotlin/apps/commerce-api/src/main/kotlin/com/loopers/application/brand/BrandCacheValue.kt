package com.loopers.application.brand

import com.loopers.domain.brand.BrandModel

/** 브랜드 캐시에 저장하는 값. 원시 타입만 담는 이유는 ProductCacheValue 와 같다. */
data class BrandCacheValue(
    val id: Long,
    val name: String,
    val description: String,
) {
    companion object {
        fun from(model: BrandModel): BrandCacheValue =
            BrandCacheValue(
                id = model.id,
                name = model.name.value,
                description = model.description.value,
            )
    }
}
