package com.loopers.interfaces.api.product

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Product V1 API", description = "Loopers 상품 API 입니다.")
interface ProductV1ApiSpec {
    @Operation(
        summary = "상품 목록 조회",
        description = "브랜드로 필터링하고 정렬 기준에 따라 페이징된 상품 목록을 반환합니다. " +
            "존재하지 않는 브랜드로 필터링하면 오류가 아니라 빈 목록을 반환합니다.",
    )
    fun getProducts(
        @Schema(name = "브랜드 ID", description = "특정 브랜드의 상품만 필터링합니다. 생략하면 전체 상품을 대상으로 합니다.")
        brandId: Long?,
        @Schema(name = "정렬 기준", description = "latest(기본값) / price_asc / likes_desc. 그 외 값은 400 입니다.")
        sort: String?,
        @Schema(name = "페이지 번호", description = "0 이상. 기본값 0")
        page: Int?,
        @Schema(name = "페이지 크기", description = "1 이상 100 이하. 기본값 20")
        size: Int?,
    ): ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>

    @Operation(
        summary = "상품 정보 조회",
        description = "상품 ID 로 상품 정보를 조회합니다. 삭제된 상품은 존재하지 않는 것으로 취급합니다. " +
            "상품의 브랜드가 삭제된 경우 brand 는 null 로 반환됩니다.",
    )
    fun getProduct(
        @Schema(name = "상품 ID", description = "조회할 상품의 ID")
        productId: Long,
    ): ApiResponse<ProductV1Dto.ProductResponse>
}
