package com.loopers.domain.like

import com.loopers.utils.DatabaseCleanUp
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.persistence.PersistenceException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.ZonedDateTime

@SpringBootTest
class ProductLikeModelPersistenceTest @Autowired constructor(
    private val databaseCleanUp: DatabaseCleanUp,
    private val transactionTemplate: TransactionTemplate,
) {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("좋아요를 저장하면, ")
    @Nested
    inner class Persist {
        @DisplayName("컬럼으로 저장되고 다시 읽을 때 복원된다.")
        @Transactional
        @Test
        fun persistsColumns_andRestoresThem() {
            // arrange
            val like = ProductLikeModel.create(userId = 7L, productId = 9L)

            // act
            entityManager.persist(like)
            entityManager.flush()
            entityManager.clear()
            val found = entityManager.find(ProductLikeModel::class.java, like.id)

            // assert
            assertAll(
                { assertThat(found.id).isPositive() },
                { assertThat(found.userId).isEqualTo(7L) },
                { assertThat(found.productId).isEqualTo(9L) },
                { assertThat(found.createdAt).isNotNull() },
                { assertThat(found.updatedAt).isNotNull() },
                { assertThat(found.deletedAt).isNull() },
            )
        }

        @DisplayName("같은 회원과 상품 조합이 이미 있으면, 유니크 제약에 걸린다.")
        @Test
        fun violatesUniqueConstraint_whenSamePairIsInsertedTwice() {
            // arrange
            saveInNewTransaction(ProductLikeModel.create(userId = 7L, productId = 9L))

            // act & assert
            assertThatThrownBy { saveInNewTransaction(ProductLikeModel.create(userId = 7L, productId = 9L)) }
                .isInstanceOf(PersistenceException::class.java)
                .hasStackTraceContaining("uk_product_likes_user_product")
        }

        /**
         * 소프트 삭제된 행도 유니크 제약에 포함된다는 것이 부활 설계의 전제다. (설계 문서 5.4 장)
         * 이 단언이 깨지면 취소 후 재좋아요를 INSERT 로 처리해도 되어버려 6장 전체가 흔들린다.
         */
        @DisplayName("소프트 삭제된 행이 있어도, 같은 조합의 새 행은 유니크 제약에 걸린다.")
        @Test
        fun violatesUniqueConstraint_evenWhenExistingRowIsSoftDeleted() {
            // arrange
            val like = saveInNewTransaction(ProductLikeModel.create(userId = 7L, productId = 9L))
            softDeleteInNewTransaction(like.id)

            // act & assert
            assertThatThrownBy { saveInNewTransaction(ProductLikeModel.create(userId = 7L, productId = 9L)) }
                .isInstanceOf(PersistenceException::class.java)
                .hasStackTraceContaining("uk_product_likes_user_product")
        }
    }

    /**
     * 제약 위반은 flush 시점에 터지고 그 트랜잭션을 오염시키므로, 각 저장을 독립 트랜잭션으로 분리한다.
     * 테스트 메서드에 @Transactional 을 붙이면 첫 위반 이후의 단언이 전부 무의미해진다.
     *
     * 이 헬퍼는 `EntityManager` 를 직접 호출하므로 Spring 의 예외 변환(`@Repository` 프록시 경계)을 지나지 않고,
     * 여기서 터지는 것은 벤더 예외(`PersistenceException`)이지 `DataIntegrityViolationException` 이 아니다.
     * 프로덕션 경로는 Spring Data 리포지터리를 지나므로 번역된 예외를 받으며, 그 계약은 `LikeFacade` 의 경합
     * 흡수(설계 6.8 장)가 의존하고 동시성 테스트가 검증한다. 따라서 이 테스트가 증명하는 것은 예외 타입이 아니라
     * 제약 이름(`uk_product_likes_user_product`)으로 확인되는 DB 수준의 사실이다.
     */
    private fun saveInNewTransaction(like: ProductLikeModel): ProductLikeModel =
        transactionTemplate.execute {
            entityManager.persist(like)
            entityManager.flush()
            like
        }!!

    private fun softDeleteInNewTransaction(id: Long) {
        transactionTemplate.execute {
            entityManager.createQuery("UPDATE ProductLikeModel l SET l.deletedAt = :now WHERE l.id = :id")
                .setParameter("now", ZonedDateTime.now())
                .setParameter("id", id)
                .executeUpdate()
        }
    }
}
