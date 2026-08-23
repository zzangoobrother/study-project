package com.loopers.interfaces.api.like

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import com.loopers.interfaces.api.product.ProductV1Dto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse

@Tag(name = "User Like V1 API", description = "Loopers 내 좋아요 API 입니다.")
interface UserLikeV1ApiSpec {
    @Operation(
        summary = "내가 좋아요한 상품 목록 조회",
        description = "최근에 좋아요한 상품이 앞에 옵니다. 취소한 좋아요와 삭제된 상품은 목록과 totalElements 양쪽에서 빠집니다. " +
            "응답의 likeCount 는 그 상품의 전체 좋아요 수이며 이 회원의 것이 아닙니다.",
    )
    fun getLikedProducts(
        @Schema(name = "로그인 ID", description = "요청 주체를 식별하는 X-Loopers-LoginId 헤더 값")
        loginId: String,
        @Schema(name = "페이지 번호", description = "0 이상. 기본값 0")
        page: Int?,
        @Schema(name = "페이지 크기", description = "1 이상 100 이하. 기본값 20")
        size: Int?,
        response: HttpServletResponse,
    ): ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>
}
