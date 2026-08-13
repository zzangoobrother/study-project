package com.loopers.interfaces.api.brand

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Brand V1 API", description = "Loopers 브랜드 API 입니다.")
interface BrandV1ApiSpec {
    @Operation(
        summary = "브랜드 정보 조회",
        description = "브랜드 ID 로 브랜드 정보를 조회합니다. 삭제된 브랜드는 존재하지 않는 것으로 취급합니다.",
    )
    fun getBrand(
        @Schema(name = "브랜드 ID", description = "조회할 브랜드의 ID")
        brandId: Long,
    ): ApiResponse<BrandV1Dto.BrandResponse>
}
