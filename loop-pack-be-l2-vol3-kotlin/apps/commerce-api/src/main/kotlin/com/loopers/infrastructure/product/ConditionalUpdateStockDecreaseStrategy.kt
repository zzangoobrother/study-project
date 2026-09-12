package com.loopers.infrastructure.product

import com.loopers.domain.product.StockDecreaseStrategy
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository

/**
 * 조건부 UPDATE 재고 차감 전략 — 현재 프로덕션 구현을 그대로 옮긴 것이다. (2026-09-09 설계 문서 6.1 장)
 *
 * matchIfMissing = true 인 이유는 두 가지다. 첫째, 이 속성은 local·test 프로필에만 준다(기준선 참고) —
 * dev·qa·prd 는 이 값을 아예 모르지만 그래도 기동은 돼야 한다. 둘째, 이 스위치 자체가 측정이 끝나면
 * 걷어질 것이므로(설계 문서 6.5 장) 그때도 조건부 UPDATE 가 기본으로 남는 쪽이 안전하다 — 셋 중
 * 이것이 지금의 실제 프로덕션 구현이기 때문이다.
 */
@Repository
@ConditionalOnProperty(
    name = ["loopers.stock.lock-strategy"],
    havingValue = "conditional-update",
    matchIfMissing = true,
)
class ConditionalUpdateStockDecreaseStrategy(
    private val productJpaRepository: ProductJpaRepository,
) : StockDecreaseStrategy {
    private val log = LoggerFactory.getLogger(ConditionalUpdateStockDecreaseStrategy::class.java)

    init {
        // 환경변수를 안 바꾸고 두 번 재는 사고가 이 비교에서 가장 흔한 실수다 (설계 문서 6.1 장).
        // 이 로그가 그 사고를 잡는 유일한 장치이므로 기동마다 반드시 찍는다.
        log.info("재고 차감 전략 선택 : conditional-update")
    }

    override fun decreaseStock(productId: Long, quantity: Int): Int {
        return productJpaRepository.decreaseStock(productId = productId, quantity = quantity)
    }
}
