package com.loopers.application.admin.order

import com.loopers.domain.order.OrderCriteria
import com.loopers.domain.order.OrderService
import com.loopers.domain.support.PageResult
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

/**
 * 주문 어드민 유스케이스.
 *
 * 공개 OrderFacade 와 마찬가지로 주문과 회원 두 애그리거트를 조합하지만,
 * 소유자 검증이 없고 탈퇴한 회원까지 채운다는 점이 다르다. (아래 getOrder KDoc 참고)
 */
@Component
class OrderAdminFacade(
    private val orderService: OrderService,
    private val userService: UserService,
) {
    /**
     * summaryOf 를 쓰는 것이 N+1 방어의 실체다. of 로 바꾸면 주문 수만큼 order_items 조회가 나간다.
     * (공개 OrderFacade.getOrders 와 같은 이유)
     */
    fun getOrders(criteria: OrderCriteria.AdminSearch): PageResult<OrderAdminInfo> {
        val orders = orderService.getOrdersForAdmin(criteria)
        val users = loadUsers(orders.content.map { it.userId })

        return orders.map { OrderAdminInfo.summaryOf(it, users[it.userId]) }
    }

    /**
     * 주문 상세.
     *
     * 소유자 검증을 하지 않는다. 공개 OrderFacade.getOrder 는 남의 주문을 404 로 숨기지만,
     * 어드민은 주문을 낸 회원이 누구든 조회할 수 있어야 하는 것이 그 존재 이유이므로
     * 소유자를 가릴 근거가 없다.
     *
     * orderService.getOrder 는 이미 fetch join 으로 항목을 채워 반환하므로 새 조회 메서드가 필요 없다.
     */
    fun getOrder(id: Long): OrderAdminInfo {
        val order = orderService.getOrder(id)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[orderId = $id] 존재하지 않는 주문입니다.",
            )

        return OrderAdminInfo.of(order, loadUsers(listOf(order.userId))[order.userId])
    }

    /**
     * userId 를 중복 제거해 IN 절 한 번으로 조회한다. 주문이 몇 건이든 이 호출은 1회다.
     *
     * getUsersIncludingDeleted 를 쓰는 이유는 findByLoginId 와 달리 탈퇴 회원을 제외하지 않아서다.
     * 탈퇴 회원을 결과에서 빼면 "탈퇴한 회원의 주문" 과 "알 수 없는 회원의 주문" 이 둘 다
     * user = null 로 뭉개진다.
     */
    private fun loadUsers(userIds: List<Long>): Map<Long, OrderAdminInfo.User> {
        return userService.getUsersIncludingDeleted(userIds.distinct())
            .associate { it.id to OrderAdminInfo.User.from(it) }
    }
}
