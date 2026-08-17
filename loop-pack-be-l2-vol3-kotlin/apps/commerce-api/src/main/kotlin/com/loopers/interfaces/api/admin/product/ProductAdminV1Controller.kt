package com.loopers.interfaces.api.admin.product

import com.loopers.application.admin.product.ProductAdminFacade
import com.loopers.domain.product.ProductCriteria
import com.loopers.domain.support.PageQuery
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 상품 어드민 API.
 *
 * 인증은 AdminAuthInterceptor 가 /api-admin 하위 경로에서 처리한다.
 * 목록에 sort 파라미터가 없는 것은 요구사항에 없기 때문이며, 정렬은 최신순 고정이다.
 */
@RestController
@RequestMapping("/api-admin/v1/products")
class ProductAdminV1Controller(
    private val productAdminFacade: ProductAdminFacade,
) : ProductAdminV1ApiSpec {
    @GetMapping
    override fun getProducts(
        @RequestParam(required = false) brandId: Long?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>> {
        val criteria = ProductCriteria.AdminSearch(
            brandId = brandId,
            pageQuery = PageQuery.of(page, size),
        )

        return productAdminFacade.getProducts(criteria)
            .let { result -> PageResponse.from(result) { ProductAdminV1Dto.ProductResponse.from(it) } }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{productId}")
    override fun getProduct(
        @PathVariable productId: Long,
    ): ApiResponse<ProductAdminV1Dto.ProductResponse> {
        return productAdminFacade.getProduct(productId)
            .let { ProductAdminV1Dto.ProductResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
