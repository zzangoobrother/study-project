package com.loopers.application.order

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderCriteria
import com.loopers.domain.order.OrderItemModel
import com.loopers.domain.order.OrderService
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductService
import com.loopers.domain.support.PageQuery
import com.loopers.domain.support.PageResult
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 회원 · 상품 · 주문 세 애그리거트를 조합하는 유스케이스.
 *
 * LikeFacade 와 달리 평범한 @Transactional 을 쓴다. (설계 문서 6.8 장)
 * 좋아요가 TransactionTemplate 을 쓴 이유는 경합 예외를 트랜잭션 경계 밖에서 흡수해야 했기 때문인데,
 * 주문은 흡수하지 않는다 — 재고 부족은 409 로 그대로 나가고 그때 롤백되는 것이 정답이다.
 * 흡수할 것이 없으므로 경계를 밖으로 뺄 이유가 없다.
 *
 * 주의: 이 API 는 인증을 수행하지 않는다. 헤더 값의 형식만 검증할 뿐 요청자가 본인인지 확인하지 않으므로,
 * 로그인 ID 를 아는 누구나 타인 명의로 주문할 수 있다. 좋아요와 같은 구조지만 결과의 무게가 다르다 —
 * 좋아요는 취소하면 원상복구되지만 주문은 재고를 소모시키고 되돌릴 경로가 이번 범위에 없다.
 * 자격 증명 검증이 추가되기 전까지 외부에 공개해서는 안 된다. (설계 문서 11.1 장)
 *
 * 쿠폰이 더해지며 이 파사드가 네 애그리거트를 잇는 지점이 되었다.
 * 조율 로직은 useCouponOrThrow 같은 private 메서드로 분리해 place 가 흐름만 읽히도록 유지한다. (설계 문서 7.2 장)
 */
@Component
class OrderFacade(
    private val userService: UserService,
    private val productService: ProductService,
    private val orderService: OrderService,
    private val couponService: CouponService,
) {
    @Transactional
    fun place(command: OrderCommand.Place): OrderInfo {
        val user = getUserOrThrow(command.loginId)

        // 정렬이 데드락을 막는다. 저장되는 항목의 순서는 요청 순서 그대로이므로 정렬한 것은 차감 순서뿐이다.
        val sorted = command.items.sortedBy { it.productId }
        val products = loadProductsOrThrow(sorted.map { it.productId })

        // 항목 조립을 차감보다 앞에 둔다. 정률 쿠폰이 총액을 기준으로 계산되므로
        // 할인 전에 totalPrice 가 확정되어야 한다. 조립은 이미 읽은 상품으로 하는 순수 계산이라
        // 차감 전후 어느 쪽에 두어도 결과가 같다. (설계 문서 6.5 장)
        val items = command.items.map { item ->
            val product = products.getValue(item.productId)
            OrderItemModel.create(
                productId = product.id,
                productName = product.name,
                unitPrice = product.price,
                quantity = item.quantity,
            )
        }
        val totalPrice = items.sumOf { it.subtotal.value }

        // 쿠폰을 재고보다 먼저 소모한다 (설계 문서 6.4 장).
        // 사용 불가능한 쿠폰이면 재고를 건드리기 전에 실패하고, 경합이 심한 products 락을 더 짧게 잡는다.
        val discountAmount = command.userCouponId
            ?.let { useCouponOrThrow(userId = user.id, userCouponId = it, totalPrice = totalPrice) }
            ?: Price.ZERO

        sorted.forEach { item ->
            if (!productService.decreaseStock(productId = item.productId, quantity = item.quantity.value)) {
                throw CoreException(
                    errorType = ErrorType.CONFLICT,
                    customMessage = "[productId = ${item.productId}] 재고가 부족하거나 주문할 수 없는 상품입니다.",
                )
            }
        }

        return OrderInfo.of(
            orderService.place(
                userId = user.id,
                items = items,
                discountAmount = discountAmount,
                usedCouponId = command.userCouponId,
            ),
        )
    }

    /**
     * 쿠폰을 조회해 할인을 계산하고 소모한다.
     *
     * 조회와 소모가 두 단계인 것은 조건부 UPDATE 가 영향 행 수만 돌려주고 행의 내용을 주지 않기 때문이다.
     * 할인 계산에 쿠폰 내용이 필요하므로 조회는 선택이 아니라 필수이며,
     * 그 조회가 자연스럽게 404 판정을 겸한다. (설계 문서 6.3 장)
     *
     * 조회와 UPDATE 사이에 다른 요청이 그 쿠폰을 써 버릴 수 있다. 그때 use 가 false 를 돌려주고 409 가 나간다.
     * 틈이 없는 것이 아니라, 틈에서 벌어진 일이 WHERE 절에 걸려 정확한 결과로 이어진다.
     */
    private fun useCouponOrThrow(userId: Long, userCouponId: Long, totalPrice: Long): Price {
        val coupon = couponService.getUserCoupon(userCouponId = userCouponId, userId = userId)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[userCouponId = $userCouponId] 존재하지 않는 쿠폰입니다.",
            )

        val discountAmount = Price(coupon.discountFor(totalPrice))

        // 이미 썼는지·만료됐는지를 구분하지 않는다. 호출자가 두 경우에 할 수 있는 일이 같다. (설계 문서 8.2 장)
        if (!couponService.use(userCouponId = userCouponId, userId = userId)) {
            throw CoreException(
                errorType = ErrorType.CONFLICT,
                customMessage = "[userCouponId = $userCouponId] 이미 사용했거나 만료된 쿠폰입니다.",
            )
        }

        return discountAmount
    }

    /**
     * 내 주문 상세.
     *
     * 소유자가 아니면 404 다. 403 이 아닌 이유는 존재 자체를 숨기기 위해서다 —
     * 403 은 "그 주문은 존재한다" 를 알려주므로 ID 를 1 부터 훑으면 주문량과 증가 속도가 드러난다.
     * 인증이 없는 현 상태에서는 남의 loginId 를 아는 사람이 그 사람의 주문 존재를 확인할 수 있다.
     * (설계 문서 4.5 장)
     *
     * OrderQueryDslRepository.findById 가 fetch join 으로 항목을 미리 초기화해 반환하므로,
     * 이 readOnly 트랜잭션이 LAZY 로딩 때문에 필요한 것은 아니다. 플러시와 더티 체크를 꺼서
     * 조회 전용 호출의 부담을 줄이는 것이 목적이다.
     */
    @Transactional(readOnly = true)
    fun getOrder(loginId: LoginId, orderId: Long): OrderInfo {
        val user = getUserOrThrow(loginId)
        val order = orderService.getOrder(orderId)

        if (order == null || order.userId != user.id) {
            throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[orderId = $orderId] 존재하지 않는 주문입니다.",
            )
        }

        return OrderInfo.of(order)
    }

    /**
     * 내 주문 목록.
     *
     * summaryOf 를 쓰는 것이 N+1 방어의 실체다. of 로 바꾸면 주문 수만큼 order_items 조회가 나간다.
     * (설계 문서 4.2 장)
     */
    @Transactional(readOnly = true)
    fun getOrders(
        loginId: LoginId,
        startAt: LocalDate?,
        endAt: LocalDate?,
        pageQuery: PageQuery,
    ): PageResult<OrderInfo> {
        val user = getUserOrThrow(loginId)
        val criteria = OrderCriteria.Search(
            userId = user.id,
            startAt = startAt,
            endAt = endAt,
            pageQuery = pageQuery,
        )

        return orderService.getOrders(criteria).map { OrderInfo.summaryOf(it) }
    }

    private fun getUserOrThrow(loginId: LoginId): UserModel =
        userService.getUser(loginId)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[loginId = ${loginId.value}] 존재하지 않는 회원입니다.",
            )

    /**
     * 삭제된 상품은 조회에서 빠지므로 요청 개수와 결과 개수가 다르면 없는 상품이 섞인 것이다.
     * 미등록과 소프트 삭제를 구분하지 않는 것은 ProductFacade.getProduct 와 같은 판단이다.
     *
     * 여기서 읽은 name 과 price 가 그대로 스냅샷이 된다. 차감 뒤에 다시 조회하지 않는 이유는,
     * 차감 UPDATE 가 name 과 price 를 건드리지 않아 다시 읽어도 같은 값이고 쿼리만 늘기 때문이다.
     */
    private fun loadProductsOrThrow(productIds: List<Long>): Map<Long, ProductModel> {
        val products = productService.getProductsByIds(productIds).associateBy { it.id }

        if (products.size != productIds.size) {
            val missing = productIds.filterNot { products.containsKey(it) }
            throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[productIds = $missing] 존재하지 않는 상품입니다.",
            )
        }

        return products
    }
}
