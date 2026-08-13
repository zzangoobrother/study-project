package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductFacade
import com.loopers.domain.product.ProductCriteria
import com.loopers.domain.product.ProductSortType
import com.loopers.domain.support.PageQuery
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 인증이 필요 없는 공개 API 다. 응답이 URL 로 완전히 결정되므로 캐시 헤더를 세팅하지 않는다.
 */
@RestController
@RequestMapping("/api/v1/products")
class ProductV1Controller(
    private val productFacade: ProductFacade,
) : ProductV1ApiSpec {
    /**
     * 쿼리 파라미터를 DTO 하나로 묶지 않고 개별 @RequestParam 으로 받는다. (설계 문서 6.7 장)
     *
     * @ModelAttribute 바인딩이 되면 ?page=abc 같은 요청이 MethodArgumentNotValidException 을 던지는데,
     * ApiControllerAdvice 에 그 핸들러가 없고 ResponseEntityExceptionHandler 를 상속하지도 않아
     * 포괄 핸들러가 잡아 500 이 나간다. 개별 파라미터면 MethodArgumentTypeMismatchException 이 되어 400 이다.
     *
     * 기본값은 @RequestParam(defaultValue = ...) 이 아니라 ProductSortType.from 과 PageQuery.of 가 갖는다.
     * 기본값이 두 곳에 흩어지면 언젠가 어긋난다.
     */
    @GetMapping
    override fun getProducts(
        @RequestParam(required = false) brandId: Long?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): ApiResponse<PageResponse<ProductV1Dto.ProductResponse>> {
        val criteria = ProductCriteria.Search(
            brandId = brandId,
            sort = ProductSortType.from(sort),
            pageQuery = PageQuery.of(page, size),
        )

        return productFacade.getProducts(criteria)
            .let { result -> PageResponse.from(result) { ProductV1Dto.ProductResponse.from(it) } }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{productId}")
    override fun getProduct(
        @PathVariable productId: Long,
    ): ApiResponse<ProductV1Dto.ProductResponse> {
        return productFacade.getProduct(productId)
            .let { ProductV1Dto.ProductResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
