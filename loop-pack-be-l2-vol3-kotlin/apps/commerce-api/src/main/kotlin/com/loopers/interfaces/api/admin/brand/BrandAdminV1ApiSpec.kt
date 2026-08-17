package com.loopers.interfaces.api.admin.brand

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Brand Admin V1 API", description = "Loopers 브랜드 어드민 API 입니다. LDAP 인증이 필요합니다.")
interface BrandAdminV1ApiSpec {
    @Operation(
        summary = "브랜드 목록 조회",
        description = "등록된 브랜드를 최신순으로 조회합니다. 삭제된 브랜드도 포함되며 deleted 로 구분합니다.",
    )
    fun getBrands(
        @Schema(name = "페이지 번호", description = "0 부터 시작합니다. 생략 시 0")
        page: Int?,
        @Schema(name = "페이지 크기", description = "1 이상 100 이하. 생략 시 20")
        size: Int?,
    ): ApiResponse<PageResponse<BrandAdminV1Dto.BrandResponse>>

    @Operation(
        summary = "브랜드 상세 조회",
        description = "브랜드 ID 로 조회합니다. 삭제된 브랜드도 200 으로 반환하며 deleted 가 true 입니다.",
    )
    fun getBrand(
        @Schema(name = "브랜드 ID", description = "조회할 브랜드의 ID")
        brandId: Long,
    ): ApiResponse<BrandAdminV1Dto.BrandResponse>
}
