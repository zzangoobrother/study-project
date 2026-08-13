package com.loopers.application.brand

import com.loopers.domain.brand.BrandDescription
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName

/**
 * 계층 밖으로 전달되는 브랜드 정보.
 * 값 객체를 그대로 들고 다니고 String 변환은 DTO 가 한다.
 */
data class BrandInfo(
    val id: Long,
    val name: BrandName,
    val description: BrandDescription,
) {
    companion object {
        fun from(model: BrandModel): BrandInfo {
            return BrandInfo(
                id = model.id,
                name = model.name,
                description = model.description,
            )
        }
    }
}
