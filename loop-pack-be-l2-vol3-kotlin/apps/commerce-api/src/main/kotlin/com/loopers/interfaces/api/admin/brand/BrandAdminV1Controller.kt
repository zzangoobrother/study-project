package com.loopers.interfaces.api.admin.brand

import com.loopers.application.admin.brand.BrandAdminFacade
import com.loopers.domain.support.PageQuery
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 브랜드 어드민 API.
 *
 * 인증 코드가 이 클래스에 없는 것은 AdminAuthInterceptor 가 /api-admin 하위를 통째로 막기 때문이다.
 * 여기에 @RequestHeader 를 두면 인증이 엔드포인트마다 복사되고, 새 엔드포인트에서 빠뜨려도 컴파일이 통과한다.
 *
 * 쿼리 파라미터를 DTO 로 묶지 않고 개별 @RequestParam 으로 받는 이유는 공개 API 와 같다.
 * @ModelAttribute 바인딩이면 ?page=abc 가 MethodArgumentNotValidException 이 되어 500 으로 나간다.
 */
@RestController
@RequestMapping("/api-admin/v1/brands")
class BrandAdminV1Controller(
    private val brandAdminFacade: BrandAdminFacade,
) : BrandAdminV1ApiSpec {
    @GetMapping
    override fun getBrands(
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): ApiResponse<PageResponse<BrandAdminV1Dto.BrandResponse>> {
        return brandAdminFacade.getBrands(PageQuery.of(page, size))
            .let { result -> PageResponse.from(result) { BrandAdminV1Dto.BrandResponse.from(it) } }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{brandId}")
    override fun getBrand(
        @PathVariable brandId: Long,
    ): ApiResponse<BrandAdminV1Dto.BrandResponse> {
        return brandAdminFacade.getBrand(brandId)
            .let { BrandAdminV1Dto.BrandResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    /**
     * 등록 응답이 201 Created 가 아니라 200 인 것은 기존 POST /api/v1/users 와 맞추기 위해서다.
     * 어드민만 201 을 쓰면 이 프로젝트에서 유일한 예외가 되고 Location 헤더 관례도 새로 정해야 한다.
     * 설계 문서 10.4 장에 기록돼 있다.
     */
    @PostMapping
    override fun register(
        @RequestBody request: BrandAdminV1Dto.RegisterRequest,
    ): ApiResponse<BrandAdminV1Dto.BrandResponse> {
        return brandAdminFacade.register(request.toCommand())
            .let { BrandAdminV1Dto.BrandResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PutMapping("/{brandId}")
    override fun change(
        @PathVariable brandId: Long,
        @RequestBody request: BrandAdminV1Dto.ChangeRequest,
    ): ApiResponse<BrandAdminV1Dto.BrandResponse> {
        return brandAdminFacade.change(request.toCommand(brandId))
            .let { BrandAdminV1Dto.BrandResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @DeleteMapping("/{brandId}")
    override fun delete(
        @PathVariable brandId: Long,
    ): ApiResponse<Any> {
        brandAdminFacade.delete(brandId)
        return ApiResponse.success()
    }
}
