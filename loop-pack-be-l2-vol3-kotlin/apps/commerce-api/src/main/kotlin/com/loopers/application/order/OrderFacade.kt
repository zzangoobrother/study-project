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
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

/**
 * 회원 · 상품 · 주문 세 애그리거트를 조합하는 유스케이스.
 *
 * place() 는 지금 TransactionTemplate 을 쓴다. 원래는 LikeFacade 와 달리 평범한 @Transactional 이었고
 * (설계 문서 6.8 장), 그때의 근거는 "주문에는 흡수할 경합 예외가 없다 — 재고 부족은 409 로 그대로
 * 나가고 그때 롤백되는 것이 정답이다" 였다. 그 근거는 조건부 UPDATE 에서는 여전히 옳다.
 *
 * 바뀐 이유는 낙관적 락 측정 때문이다. 낙관적 락은 버전 충돌을 재시도해야 하고, 재시도는 트랜잭션
 * 경계 밖이어야 한다 — 안에 두면 롤백되지 않은 트랜잭션 위에서 다시 시도하게 된다. 그래서 LikeFacade
 * 와 같은 구조(얇은 래퍼 + transactionTemplate.execute)를 빌려 왔다. (2026-09-09 설계 문서 6.2 장)
 *
 * 세 전략 비교가 끝나 조건부 UPDATE 가 채택되면 이 래퍼는 걷히고 @Transactional 로 돌아간다.
 * (2026-09-09 설계 문서 6.5 장) 그때 위 첫 문단의 근거가 다시 그대로 유효해진다.
 *
 * 주의: 이 API 는 인증을 수행하지 않는다. 헤더 값의 형식만 검증할 뿐 요청자가 본인인지 확인하지 않으므로,
 * 로그인 ID 를 아는 누구나 타인 명의로 주문할 수 있다. 좋아요와 같은 구조지만 결과의 무게가 다르다 —
 * 좋아요는 취소하면 원상복구되지만 주문은 재고를 소모시키고 되돌릴 경로가 이번 범위에 없다.
 * 자격 증명 검증이 추가되기 전까지 외부에 공개해서는 안 된다. (설계 문서 11.1 장)
 *
 * 쿠폰이 더해지며 이 파사드가 네 애그리거트를 잇는 지점이 되었다.
 * 조율 로직은 useCouponOrThrow 같은 private 메서드로 분리해 place 가 흐름만 읽히도록 유지한다. (설계 문서 7.2 장)
 * 최소 주문 금액은 조건부 UPDATE 가 아니라 이 파사드가 판정한다. 경합하지 않는 조건이기 때문이다. (2026-09-01 설계 문서 6.3 장)
 */
@Component
class OrderFacade(
    private val userService: UserService,
    private val productService: ProductService,
    private val orderService: OrderService,
    private val couponService: CouponService,
    private val transactionTemplate: TransactionTemplate,
) {
    fun place(command: OrderCommand.Place): OrderInfo {
        repeat(MAX_OPTIMISTIC_LOCK_ATTEMPTS) { attempt ->
            try {
                return transactionTemplate.execute { placeInTransaction(command) }!!
            } catch (e: ObjectOptimisticLockingFailureException) {
                // 백오프 없음 — 넣으면 지연이 경합의 산물인지 정책의 산물인지 구분할 수 없다.
                // (2026-09-09 설계 문서 6.2 장) 다른 두 전략에서는 이 예외가 나지 않으므로
                // 이 catch 는 그 전략들에서 한 번도 실행되지 않는다 — 핫패스에 분기를 더하지 않는다.
                if (attempt == MAX_OPTIMISTIC_LOCK_ATTEMPTS - 1) {
                    throw CoreException(
                        errorType = ErrorType.CONFLICT,
                        customMessage = "[productIds = ${command.items.map { it.productId }}] " +
                            "동시 갱신 충돌로 재고 차감에 실패했습니다 " +
                            "(재시도 $MAX_OPTIMISTIC_LOCK_ATTEMPTS 회 초과).",
                    )
                }
            }
        }
        error("도달할 수 없다 — 위 루프가 성공 시 반환하거나 재시도 초과 시 예외를 던진다")
    }

    /**
     * 실제 주문 처리. place() 와 분리된 이유는 재시도가 트랜잭션 경계 밖에 있어야 하기 때문이다
     * (2026-09-09 설계 문서 6.2 장) — 재시도를 @Transactional 안에 두면 실패한 트랜잭션이 롤백되지
     * 않은 채로 다시 시도하게 된다. LikeFacade 가 같은 문제를 TransactionTemplate 으로 푼 전례를
     * 그대로 따른다 — "얇은 래퍼 + @Transactional 컴포넌트" 로 클래스를 쪼개지 않는 이유는
     * LikeFacade KDoc 참고.
     */
    private fun placeInTransaction(command: OrderCommand.Place): OrderInfo {
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

        // 쿠폰을 재고보다 먼저 소모한다 (2026-08-30 설계 문서 6.4 장).
        // 사용 불가능한 쿠폰이면 재고를 건드리기 전에 실패하고, 경합이 심한 products 락을 더 짧게 잡는다.
        val applied = command.couponId
            ?.let { useCouponOrThrow(userId = user.id, couponId = it, totalPrice = totalPrice) }
        val discountAmount = applied?.discountAmount ?: Price.ZERO

        // 주문 저장을 재고 차감보다 앞으로 옮긴다. 부하 테스트로 실측한 병목이 products 배타 락
        // 보유 시간이었다 — 락을 잡은 채로 orders/order_items INSERT 까지 끝내고 있었다. 재고 차감이
        // 실패하면 CoreException 이 트랜잭션 전체를 롤백시키므로, 먼저 저장해도 정합성은 그대로다.
        // (2026-09-06 부하 테스트, 377 TPS 상한의 원인)
        val order = orderService.place(
            userId = user.id,
            items = items,
            discountAmount = discountAmount,
            usedCouponId = applied?.userCouponId,
        )

        // ⚠️ OrderInfo 변환을 재고 차감보다 반드시 먼저 한다. ProductJpaRepository.decreaseStock 이
        // @Modifying(clearAutomatically = true) 라 호출될 때마다 영속성 컨텍스트를 통째로 비우고,
        // 그러면 방금 저장한 order 가 detach 된다. 차감을 먼저 하고 order.items 를 나중에 읽으면
        // 컬렉션 초기화 여부에 따라 실패할 수 있다. "변환은 마지막에 하는 게 자연스럽다" 며
        // 이 순서를 되돌리면 이 자리가 조용히 깨진다.
        val orderInfo = OrderInfo.of(order)

        // 0 행은 재고 부족과 상품 소멸을 함께 뜻한다. 구분하지 않는다 —
        // 주문할 수 없다는 결론이 같고, 나누려면 다시 조회해야 하는데 그 조회도 같은 경합을 겪는다.
        sorted.forEach { item ->
            if (!productService.decreaseStock(productId = item.productId, quantity = item.quantity.value)) {
                throw CoreException(
                    errorType = ErrorType.CONFLICT,
                    customMessage = "[productId = ${item.productId}] 재고가 부족하거나 주문할 수 없는 상품입니다.",
                )
            }
        }

        return orderInfo
    }

    /**
     * 적용된 쿠폰. 할인 금액과 발급 ID 를 함께 나른다.
     *
     * 발급 ID 가 필요한 이유는 orders.used_coupon_id 가 정책이 아니라 발급분을 가리키기 때문이다.
     * 요청은 정책 ID 로 오지만 기록은 발급분이어야 추적이 정확하다. (2026-09-01 설계 문서 4.5 장)
     */
    private data class AppliedCoupon(val userCouponId: Long, val discountAmount: Price)

    /**
     * 쿠폰을 조회해 할인을 계산하고 소모한다.
     *
     * 조회와 소모가 두 단계인 것은 조건부 UPDATE 가 영향 행 수만 돌려주고 행의 내용을 주지 않기 때문이다.
     * 할인 계산에 쿠폰 내용이 필요하므로 조회는 선택이 아니라 필수이며,
     * 그 조회가 자연스럽게 404 판정을 겸한다. (2026-08-30 설계 문서 6.3 장)
     *
     * 조회와 UPDATE 사이에 다른 요청이 그 쿠폰을 써 버릴 수 있다. 그때 use 가 false 를 돌려주고 409 가 나간다.
     * 틈이 없는 것이 아니라, 틈에서 벌어진 일이 WHERE 절에 걸려 정확한 결과로 이어진다.
     */
    private fun useCouponOrThrow(userId: Long, couponId: Long, totalPrice: Long): AppliedCoupon {
        val coupon = couponService.getUserCoupon(couponId = couponId, userId = userId)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[couponId = $couponId] 발급받지 않았거나 존재하지 않는 쿠폰입니다.",
            )

        // 경합하지 않는 조건이라 조건부 UPDATE 의 WHERE 가 아니라 여기서 판정한다. (2026-09-01 설계 문서 6.3 장)
        // 사용·만료와 달리 400 인 이유는 호출자가 할 수 있는 일이 다르기 때문이다 — 더 담으면 쓸 수 있다.
        if (totalPrice < coupon.minOrderAmount) {
            throw CoreException(
                errorType = ErrorType.BAD_REQUEST,
                customMessage = "[couponId = $couponId] 최소 주문 금액 ${coupon.minOrderAmount} 원 이상부터 사용할 수 있습니다.",
            )
        }

        val discountAmount = Price(coupon.discountFor(totalPrice))

        // 이미 썼는지·만료됐는지를 구분하지 않는다. 호출자가 두 경우에 할 수 있는 일이 같다.
        // (2026-08-30 설계 문서 8.2 장)
        if (!couponService.use(couponId = couponId, userId = userId)) {
            throw CoreException(
                errorType = ErrorType.CONFLICT,
                customMessage = "[couponId = $couponId] 이미 사용했거나 만료된 쿠폰입니다.",
            )
        }

        return AppliedCoupon(userCouponId = coupon.id, discountAmount = discountAmount)
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

    private companion object {
        /** 낙관적 락 외 전략에서는 영향을 주지 않는다 — 그 전략들은 첫 시도에서 항상 끝난다. */
        const val MAX_OPTIMISTIC_LOCK_ATTEMPTS = 3
    }
}
