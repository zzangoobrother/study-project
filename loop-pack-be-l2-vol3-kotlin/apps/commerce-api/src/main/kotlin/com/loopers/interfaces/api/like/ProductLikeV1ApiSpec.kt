package com.loopers.interfaces.api.like

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Product Like V1 API", description = "Loopers 상품 좋아요 API 입니다.")
interface ProductLikeV1ApiSpec {
    @Operation(
        summary = "상품 좋아요 등록",
        description = "요청은 멱등합니다. 이미 좋아요한 상품에 다시 요청해도 200 이며 좋아요 수는 늘지 않습니다. " +
            "삭제된 상품은 존재하지 않는 것으로 취급해 404 입니다.",
    )
    fun like(
        @Schema(name = "로그인 ID", description = "요청 주체를 식별하는 X-Loopers-LoginId 헤더 값")
        loginId: String,
        @Schema(name = "상품 ID", description = "좋아요할 상품의 ID")
        productId: Long,
    ): ApiResponse<Any>

    @Operation(
        summary = "상품 좋아요 취소",
        description = "요청은 멱등합니다. 좋아요하지 않은 상품을 취소해도 200 이며 좋아요 수는 줄지 않습니다.",
    )
    fun unlike(
        @Schema(name = "로그인 ID", description = "요청 주체를 식별하는 X-Loopers-LoginId 헤더 값")
        loginId: String,
        @Schema(name = "상품 ID", description = "좋아요를 취소할 상품의 ID")
        productId: Long,
    ): ApiResponse<Any>
}
