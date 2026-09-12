package com.loopers.infrastructure.product

import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockDecreaseStrategy
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.support.TransactionTemplate

/**
 * Step 4 KDoc 의 가정을 증명한다 — Repository 스테레오타입이 붙은 전략이 던지는
 * jakarta.persistence.OptimisticLockException 이 스프링 표준
 * ObjectOptimisticLockingFailureException 으로 번역되는가.
 *
 * 이 번역이 없으면 OrderFacade 의 재시도 catch 가 예외를 알아보지 못해 낙관적 락이 재시도 없이
 * 전부 실패한다. 단위 테스트는 이미 번역된 예외를 스텁하므로 그 고장을 잡지 못한다.
 * (2026-09-09 설계 문서 6.2 장)
 */
@SpringBootTest(properties = ["loopers.stock.lock-strategy=optimistic"])
class OptimisticLockExceptionTranslationTest @Autowired constructor(
    private val stockDecreaseStrategy: StockDecreaseStrategy,
    private val productJpaRepository: ProductJpaRepository,
    private val transactionTemplate: TransactionTemplate,
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("버전이 어긋난 채 차감하면, 스프링 표준 낙관적 락 예외로 번역되어 올라온다.")
    @Test
    fun translatesToSpringException_whenVersionConflicts() {
        // arrange
        val saved = productJpaRepository.save(
            ProductModel.create(
                brandId = 1L,
                name = ProductName("운동화"),
                price = Price(39_000),
                stock = Stock(10),
            ),
        )

        // act
        val thrown = assertThrows<Exception> {
            transactionTemplate.execute {
                // 엔티티를 1 차 캐시에 올린다 (version = 0).
                productJpaRepository.findByIdAndDeletedAtIsNull(saved.id)

                // Hibernate 가 모르는 경로로 같은 행의 version 을 밀어 올린다.
                // 이제 1 차 캐시의 version(0) 과 DB 의 version(1) 이 어긋난다.
                jdbcTemplate.update("UPDATE products SET version = version + 1 WHERE id = ?", saved.id)

                // 전략이 stock 을 줄이고 flush 하면 UPDATE ... WHERE version = 0 이 0 행이 된다.
                stockDecreaseStrategy.decreaseStock(productId = saved.id, quantity = 1)
            }
        }

        // assert
        // 여기서 jakarta.persistence.OptimisticLockException 이 잡히면 번역이 동작하지 않는 것이다.
        assertThat(thrown)
            .describedAs("번역이 없으면 OrderFacade 의 재시도 catch 가 이 예외를 알아보지 못한다")
            .isInstanceOf(ObjectOptimisticLockingFailureException::class.java)
    }
}
