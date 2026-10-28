package com.loopers.domain.order

import com.loopers.domain.support.PageResult
import java.time.ZonedDateTime

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

    /**
     * 현재 상태가 expected 중 하나일 때만 next 로 바꾼다. 바꿨으면 true.
     * false 는 "다른 요청이 먼저 바꿨다" 와 "주문이 없다" 를 함께 뜻한다. 호출자는 조회로 존재를 이미 확인한 뒤 부른다.
     */
    fun changeStatus(orderId: Long, expected: Set<OrderStatus>, next: OrderStatus): Boolean

    /**
     * 상태가 statuses 중 하나이고 createdBefore 이전(같은 시각 포함)에 생성된 주문의 ID 를 id 오름차순으로 최대 limit 건 돌려준다.
     * 소프트 삭제된 주문은 제외된다. 저장소는 "만료" 가 무엇인지 모르고 상태 집합만 받는다.
     */
    fun findExpirationTargets(statuses: Set<OrderStatus>, createdBefore: ZonedDateTime, limit: Int): List<Long>
}
