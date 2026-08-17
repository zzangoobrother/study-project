package com.loopers.interfaces.api.admin.product

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Product Admin V1 API", description = "Loopers 상품 어드민 API 입니다. LDAP 인증이 필요합니다.")
interface ProductAdminV1ApiSpec {
    @Operation(
        summary = "상품 목록 조회",
        description = "등록된 상품을 최신순으로 조회합니다. 삭제된 상품도 포함되며 deleted 로 구분합니다.",
    )
    fun getProducts(
        @Schema(name = "브랜드 ID", description = "필터 조건. 생략하면 전체 브랜드가 대상입니다.")
        brandId: Long?,
        @Schema(name = "페이지 번호", description = "0 부터 시작합니다. 생략 시 0")
        page: Int?,
        @Schema(name = "페이지 크기", description = "1 이상 100 이하. 생략 시 20")
        size: Int?,
    ): ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>

    @Operation(
        summary = "상품 상세 조회",
        description = "상품 ID 로 조회합니다. 삭제된 상품도 200 으로 반환하며 deleted 가 true 입니다.",
    )
    fun getProduct(
        @Schema(name = "상품 ID", description = "조회할 상품의 ID")
        productId: Long,
    ): ApiResponse<ProductAdminV1Dto.ProductResponse>

    @Operation(
        summary = "상품 등록",
        description = "상품을 등록합니다. brandId 는 이미 등록된(삭제되지 않은) 브랜드여야 하며, 아니면 400 입니다.",
    )
    fun register(
        request: ProductAdminV1Dto.RegisterRequest,
    ): ApiResponse<ProductAdminV1Dto.ProductResponse>

    @Operation(
        summary = "상품 정보 수정",
        description = "이름과 가격을 전체 교체합니다. 브랜드는 수정할 수 없습니다. 삭제된 상품은 409 Conflict 입니다.",
    )
    fun change(
        @Schema(name = "상품 ID", description = "수정할 상품의 ID")
        productId: Long,
        request: ProductAdminV1Dto.ChangeRequest,
    ): ApiResponse<ProductAdminV1Dto.ProductResponse>

    @Operation(
        summary = "상품 삭제",
        description = "상품을 소프트 삭제합니다. 이미 삭제된 상품에 대해서도 200 입니다.",
    )
    fun delete(
        @Schema(name = "상품 ID", description = "삭제할 상품의 ID")
        productId: Long,
    ): ApiResponse<Any>
}
