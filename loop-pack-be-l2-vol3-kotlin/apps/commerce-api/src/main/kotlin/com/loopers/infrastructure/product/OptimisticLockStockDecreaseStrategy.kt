package com.loopers.infrastructure.product

import com.loopers.domain.product.StockDecreaseStrategy
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository

/**
 * 낙관적 락(@Version) 재고 차감 전략. (2026-09-09 설계 문서 6.2 장)
 *
 * SELECT 는 락을 잡지 않는다. 재고 검사도 락 없이 앱에서 한다. 실제 방어는 stock 을 줄인 뒤
 * 강제 flush 에서 @Version 이 발동시키는 `UPDATE ... WHERE id=? AND version=?` 뿐이다 — 그 문장이
 * 0 행이면 Hibernate 가 OptimisticLockException 을 던진다.
 *
 * @Repository 를 붙이는 이유는 스프링의 PersistenceExceptionTranslationPostProcessor 가 이
 * 스테레오타입이 붙은 빈만 감싸기 때문이다. 그 감쌈이 있어야 이 메서드가 던지는
 * jakarta.persistence.OptimisticLockException 이 OrderFacade 가 잡는 스프링 표준 예외인
 * ObjectOptimisticLockingFailureException 으로 번역된다. 이 번역이 없으면 재시도 루프가 이 예외를
 * 알아보지 못하고 그대로 흘려보낸다 — 태스크 2 Step 8 에서 실제로 번역되는지 통합 테스트로 확인한다.
 *
 * 재시도는 여기 없다. "트랜잭션 경계 밖에서 OrderFacade.place 전체를 다시 부르는 형태" 로 두었으므로
 * (설계 문서 6.2 장) 이 클래스는 실패를 예외로 알리기만 하고 재시도는 모른다.
 */
@Repository
@ConditionalOnProperty(name = ["loopers.stock.lock-strategy"], havingValue = "optimistic")
class OptimisticLockStockDecreaseStrategy(
    private val productJpaRepository: ProductJpaRepository,
    private val entityManager: EntityManager,
) : StockDecreaseStrategy {
    private val log = LoggerFactory.getLogger(OptimisticLockStockDecreaseStrategy::class.java)

    init {
        log.info("재고 차감 전략 선택 : optimistic (재시도 상한 3, 백오프 없음)")
    }

    override fun decreaseStock(productId: Long, quantity: Int): Int {
        val product = productJpaRepository.findByIdAndDeletedAtIsNull(productId) ?: return 0
        if (product.stock.value < quantity) return 0

        product.decreaseStockForOptimisticLock(quantity)

        // 항목마다 즉시 flush 해 버전 충돌을 그 자리에서 드러낸다. 커밋까지 미루면 다중 항목 중
        // 몇 번째에서 충돌했는지 알 수 없고, 이 메서드의 반환값이 실제 결과와 어긋난다.
        // (여기서 예외가 나면 아래 return 문에 도달하지 않는다 — 그것이 실패를 알리는 방식이다)
        entityManager.flush()

        return 1
    }
}
