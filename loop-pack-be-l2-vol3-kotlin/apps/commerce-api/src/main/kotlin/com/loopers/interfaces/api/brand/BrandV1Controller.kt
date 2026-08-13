package com.loopers.interfaces.api.brand

import com.loopers.application.brand.BrandFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 인증이 필요 없는 공개 API 다.
 *
 * GET /api/v1/users/me 와 달리 Cache-Control / Vary 헤더를 세팅하지 않는다.
 * 응답이 헤더가 아니라 URL 로 완전히 결정되므로 공유 캐시가 다른 사용자에게 재사용해도 유출될 것이 없다.
 */
@RestController
@RequestMapping("/api/v1/brands")
class BrandV1Controller(
    private val brandFacade: BrandFacade,
) : BrandV1ApiSpec {
    @GetMapping("/{brandId}")
    override fun getBrand(
        @PathVariable brandId: Long,
    ): ApiResponse<BrandV1Dto.BrandResponse> {
        return brandFacade.getBrand(brandId)
            .let { BrandV1Dto.BrandResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
