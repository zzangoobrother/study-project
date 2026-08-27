package com.loopers.domain.order

import com.loopers.domain.support.PageResult

interface OrderRepository {
    /** 항목은 cascade 로 함께 저장된다. 호출자가 따로 저장하지 않는다. */
    fun save(order: OrderModel): OrderModel

    /**
     * 소유자 검증은 하지 않는다. 남의 주문을 404 로 볼지는 Facade 가 정한다.
     * 소프트 삭제된 주문은 제외된다.
     */
    fun findById(id: Long): OrderModel?

    /** 회원 본인의 주문만, 최근순으로 페이징 조회한다. 기간이 null 이면 그 방향의 제한이 없다. */
    fun findAll(criteria: OrderCriteria.Search): PageResult<OrderModel>

    /** 회원을 가리지 않고 최근순으로 페이징 조회한다. 소프트 삭제된 주문은 제외된다. */
    fun findAllForAdmin(criteria: OrderCriteria.AdminSearch): PageResult<OrderModel>
}
