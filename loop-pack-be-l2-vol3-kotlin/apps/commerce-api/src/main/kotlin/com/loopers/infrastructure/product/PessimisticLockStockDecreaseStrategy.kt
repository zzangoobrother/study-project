package com.loopers.infrastructure.product

import com.loopers.domain.product.StockDecreaseStrategy
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository

/**
 * 비관적 락(SELECT ... FOR UPDATE) 재고 차감 전략. (2026-09-09 설계 문서 6.3 장)
 *
 * 항목마다 왕복 2 회다 — 잠금 SELECT 하나, 검사 후 UPDATE 하나. IN 절로 여러 상품을 한 번에
 * 잠그지 않는다 — 그러면 조건부 UPDATE(항목당 왕복 1)와 비교할 때 이 전략이 부당하게 유리해진다.
 *
 * UPDATE 는 기존 ProductJpaRepository.decreaseStock 을 그대로 재사용한다. 이미 FOR UPDATE 로
 * 행을 잠그고 재고를 확인한 뒤라 그 WHERE stock >= :quantity 는 사실상 항상 참이지만, 그 문장이
 * updated_at 을 건드리지 않는다는 사실까지 함께 재사용하기 위해 새 UPDATE 문을 만들지 않는다.
 *
 * 정확히 말하면 항상 참은 아니다. OrderFacade 가 차감 전에 상품을 이미 영속성 컨텍스트에 올려
 * 두므로, 첫 항목의 findByIdForUpdate 는 락은 실제로 잡지만 낡은 인스턴스를 돌려줄 수 있다.
 * 그때 앱 검사는 통과하고 이 WHERE 절이 0 행으로 막는다 — 재사용이 방어적인 진짜 이유다.
 *
 * OrderFacade.place 의 productId 오름차순 정렬이 여기서는 이중 안전장치가 아니라 필수 조건이다.
 * 여러 문장이 순차로 락을 잡으므로 순서가 통일되지 않으면 데드락이 난다.
 */
@Repository
@ConditionalOnProperty(name = ["loopers.stock.lock-strategy"], havingValue = "pessimistic")
class PessimisticLockStockDecreaseStrategy(
    private val productJpaRepository: ProductJpaRepository,
) : StockDecreaseStrategy {
    private val log = LoggerFactory.getLogger(PessimisticLockStockDecreaseStrategy::class.java)

    init {
        log.info("재고 차감 전략 선택 : pessimistic")
    }

    override fun decreaseStock(productId: Long, quantity: Int): Int {
        val locked = productJpaRepository.findByIdForUpdate(productId) ?: return 0
        if (locked.stock.value < quantity) return 0

        return productJpaRepository.decreaseStock(productId = productId, quantity = quantity)
    }
}
