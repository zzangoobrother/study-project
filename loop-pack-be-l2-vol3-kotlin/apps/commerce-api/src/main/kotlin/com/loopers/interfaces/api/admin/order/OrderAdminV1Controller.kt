package com.loopers.interfaces.api.admin.order

import com.loopers.application.admin.order.OrderAdminFacade
import com.loopers.domain.order.OrderCriteria
import com.loopers.domain.support.PageQuery
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 주문 어드민 API.
 *
 * 인증은 AdminAuthInterceptor 가 /api-admin 하위 경로에서 처리한다.
 * 회원 필터가 없는 것은 어드민이 전체 회원의 주문을 보기 때문이다. 기간·정렬 파라미터가 없는 것은
 * 요구사항에 없기 때문이며, 정렬은 최신순 고정이다.
 */
@RestController
@RequestMapping("/api-admin/v1/orders")
class OrderAdminV1Controller(
    private val orderAdminFacade: OrderAdminFacade,
) : OrderAdminV1ApiSpec {
    @GetMapping
    override fun getOrders(
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): ApiResponse<PageResponse<OrderAdminV1Dto.OrderSummaryResponse>> {
        val criteria = OrderCriteria.AdminSearch(pageQuery = PageQuery.of(page, size))

        return orderAdminFacade.getOrders(criteria)
            .let { result -> PageResponse.from(result) { OrderAdminV1Dto.OrderSummaryResponse.from(it) } }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{orderId}")
    override fun getOrder(
        @PathVariable orderId: Long,
    ): ApiResponse<OrderAdminV1Dto.OrderResponse> {
        return orderAdminFacade.getOrder(orderId)
            .let { OrderAdminV1Dto.OrderResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
