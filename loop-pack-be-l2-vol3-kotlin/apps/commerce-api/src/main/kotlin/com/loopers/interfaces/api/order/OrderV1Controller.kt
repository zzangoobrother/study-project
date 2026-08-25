package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderFacade
import com.loopers.domain.support.PageQuery
import com.loopers.domain.user.LoginId
import com.loopers.interfaces.api.ApiHeaders
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import jakarta.servlet.http.HttpServletResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 주문 API.
 *
 * 쿼리 파라미터를 DTO 로 묶지 않고 개별 RequestParam 으로 받는 이유는 ProductV1Controller 와 같다.
 * ModelAttribute 바인딩이면 page=abc 가 500 이 되고, 개별 파라미터면 400 이 된다.
 * 기본값은 RequestParam(defaultValue = ...) 이 아니라 PageQuery.of 가 소유한다.
 *
 * 주의: 이 API 는 인증을 수행하지 않는다. 헤더 값의 형식만 검증할 뿐 요청자가 본인인지 확인하지 않으므로,
 * 로그인 ID 를 아는 누구나 타인 명의로 주문할 수 있다. 좋아요와 같은 구조지만 결과의 무게가 다르다 —
 * 좋아요는 취소하면 원상복구되지만 주문은 재고를 소모시키고 되돌릴 경로가 이번 범위에 없다.
 * 악의적 요청 하나가 인기 상품의 재고를 전부 소진시킬 수 있다.
 * 자격 증명 검증이 추가되기 전까지 외부에 공개해서는 안 된다. (설계 문서 11.1 장)
 */
@RestController
@RequestMapping("/api/v1/orders")
class OrderV1Controller(
    private val orderFacade: OrderFacade,
) : OrderV1ApiSpec {
    /**
     * 201 이 아니라 200 이다. 이 프로젝트의 다른 쓰기 API 와 형식을 맞춘다.
     * 생성된 주문의 위치를 Location 헤더로 알려주는 규약이 아직 없다.
     */
    @PostMapping
    override fun place(
        @RequestHeader(ApiHeaders.LOGIN_ID) loginId: String,
        @RequestBody request: OrderV1Dto.PlaceRequest,
    ): ApiResponse<OrderV1Dto.OrderResponse> {
        return orderFacade.place(request.toCommand(LoginId(loginId)))
            .let { OrderV1Dto.OrderResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping
    override fun getOrders(
        @RequestHeader(ApiHeaders.LOGIN_ID) loginId: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startAt: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endAt: LocalDate?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
        response: HttpServletResponse,
    ): ApiResponse<PageResponse<OrderV1Dto.OrderSummaryResponse>> {
        // 응답이 URL 이 아닌 헤더에 따라 달라지므로, Vary 없이는 공유 캐시가 다른 사용자에게 이 응답을 재사용한다.
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        response.setHeader(HttpHeaders.VARY, ApiHeaders.LOGIN_ID)

        return orderFacade.getOrders(LoginId(loginId), startAt, endAt, PageQuery.of(page, size))
            .let { result -> PageResponse.from(result) { OrderV1Dto.OrderSummaryResponse.from(it) } }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{orderId}")
    override fun getOrder(
        @RequestHeader(ApiHeaders.LOGIN_ID) loginId: String,
        @PathVariable orderId: Long,
        response: HttpServletResponse,
    ): ApiResponse<OrderV1Dto.OrderResponse> {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        response.setHeader(HttpHeaders.VARY, ApiHeaders.LOGIN_ID)

        return orderFacade.getOrder(LoginId(loginId), orderId)
            .let { OrderV1Dto.OrderResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
