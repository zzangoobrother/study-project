package com.loopers.interfaces.api.like

import com.loopers.application.like.LikeFacade
import com.loopers.domain.support.PageQuery
import com.loopers.domain.user.LoginId
import com.loopers.interfaces.api.ApiHeaders
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import com.loopers.interfaces.api.product.ProductV1Dto
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 회원에 딸린 좋아요 목록.
 *
 * 경로가 users/{userId} 가 아니라 users/me 인 이유는 세 가지다 — 등록·취소 URI 에 사용자가 없어 주체가
 * 헤더에서 와야 하고, MeResponse 가 의도적으로 id 를 노출하지 않아 클라이언트가 자기 userId 를 모르며,
 * 인증이 없는 상태에서 남의 목록을 지목할 수 있는 URL 을 만들지 않기 위해서다. (설계 문서 4.2 장)
 *
 * 쿼리 파라미터를 DTO 로 묶지 않고 개별 RequestParam 으로 받는 이유는 ProductV1Controller 와 같다.
 * ModelAttribute 바인딩이면 page=abc 가 500 이 되고, 개별 파라미터면 400 이 된다.
 */
@RestController
@RequestMapping("/api/v1/users/me/likes")
class UserLikeV1Controller(
    private val likeFacade: LikeFacade,
) : UserLikeV1ApiSpec {
    @GetMapping
    override fun getLikedProducts(
        @RequestHeader(ApiHeaders.LOGIN_ID) loginId: String,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
        response: HttpServletResponse,
    ): ApiResponse<PageResponse<ProductV1Dto.ProductResponse>> {
        // 응답이 URL 이 아닌 헤더에 따라 달라지므로, Vary 없이는 공유 캐시가 다른 사용자에게 이 응답을 재사용한다.
        // GET /api/v1/users/me 가 같은 이유로 같은 처리를 한다.
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        response.setHeader(HttpHeaders.VARY, ApiHeaders.LOGIN_ID)

        return likeFacade.getLikedProducts(LoginId(loginId), PageQuery.of(page, size))
            .let { result -> PageResponse.from(result) { ProductV1Dto.ProductResponse.from(it) } }
            .let { ApiResponse.success(it) }
    }
}
