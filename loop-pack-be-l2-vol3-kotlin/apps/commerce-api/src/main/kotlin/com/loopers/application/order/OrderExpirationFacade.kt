package com.loopers.application.order

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.OrderService
import com.loopers.domain.product.ProductService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.ZonedDateTime

/**
 * 결제 기한이 지난 주문을 취소하고 재고 · 쿠폰을 되돌린다. 세 애그리거트(주문 · 상품 · 쿠폰)를 잇는 것은 application 의 일이다.
 * (2026-10-28 주문 만료 설계 5 장)
 *
 * 한 건의 상태 전이와 복구는 한 트랜잭션이다 — 취소됐는데 재고가 안 돌아오거나 그 반대가 없다.
 * 건마다 별도 트랜잭션이라 한 건의 실패가 나머지를 막지 않는다. [expireOverdue] 자체는 트랜잭션 밖이다.
 */
@Component
class OrderExpirationFacade(
    private val orderService: OrderService,
    private val productService: ProductService,
    private val couponService: CouponService,
    private val transactionTemplate: TransactionTemplate,
) {
    companion object {
        /** 기준은 생성 시각이다. 상태 전이는 updated_at 을 건드리지 않아 결제 실패 시각을 알 수 없다. (2026-10-28 주문 만료 설계 3 장) */
        private val EXPIRE_AFTER: Duration = Duration.ofMinutes(30)

        /** 결제 동기화(SYNC_BATCH_SIZE)와 같은 규모다. */
        private const val BATCH_SIZE = 100
    }

    private val log = LoggerFactory.getLogger(OrderExpirationFacade::class.java)

    /** 스케줄러가 부른다. 한 건의 실패가 나머지를 막지 않게 건 단위로 잡는다. 뽑은 건수를 돌려준다. */
    fun expireOverdue(now: ZonedDateTime = ZonedDateTime.now()): Int {
        val targets = orderService.findExpirationTargets(createdBefore = now.minus(EXPIRE_AFTER), limit = BATCH_SIZE)
        targets.forEach { orderId ->
            runCatching { expire(orderId) }
                .onFailure { log.error("[orderId = {}] 주문 만료 처리에 실패했습니다.", orderId, it) }
        }
        return targets.size
    }

    /**
     * 한 건의 만료. 취소했으면 true.
     *
     * 판정은 [OrderService.cancel] 의 조건부 UPDATE 가 한다. 결제 시작과 같은 행 · 같은 기대 상태 집합이라 하나만 1 행을 얻는다.
     * false 면 그 사이 결제가 시작됐거나 다른 인스턴스가 먼저 취소한 것이므로 복구하지 않는다. (2026-10-28 주문 만료 설계 4.2 장)
     * 분산 락은 두지 않는다.
     */
    fun expire(orderId: Long): Boolean = transactionTemplate.execute {
        val order = orderService.getOrder(orderId) ?: return@execute false

        // cancel 의 조건부 UPDATE 가 clearAutomatically 라 호출 뒤 주문 엔티티가 detach 된다. 먼저 꺼내 둔다.
        // productId 오름차순은 주문 생성의 차감 순서와 같아야 products 행 락을 반대 순서로 잡는 데드락이 없다.
        val items = order.items.map { it.productId to it.quantity.value }.sortedBy { it.first }
        val usedCouponId = order.usedCouponId

        if (!orderService.cancel(orderId)) return@execute false

        items.forEach { (productId, quantity) ->
            // 0 행(상품 행 없음)은 정상 경로에서 생기지 않는다. 롤백하면 나머지 항목 · 쿠폰까지 막히므로 남기고 계속한다.
            if (!productService.increaseStock(productId, quantity)) {
                log.warn("[orderId = {}, productId = {}] 재고를 되돌릴 상품이 없어 건너뜁니다.", orderId, productId)
            }
        }
        if (usedCouponId != null && !couponService.restore(usedCouponId)) {
            log.warn("[orderId = {}, userCouponId = {}] 되돌릴 쿠폰 사용 이력이 없어 건너뜁니다.", orderId, usedCouponId)
        }
        log.info("[orderId = {}] 결제 기한이 지나 주문을 취소하고 재고 · 쿠폰을 되돌렸습니다.", orderId)
        true
    }!!
}
