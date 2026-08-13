package com.loopers.domain.product

import com.loopers.utils.DatabaseCleanUp
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
class ProductModelPersistenceTest @Autowired constructor(
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("상품을 저장하면, ")
    @Nested
    inner class Persist {
        @DisplayName("값 객체가 컬럼으로 풀려 저장되고 다시 읽을 때 복원된다.")
        @Transactional
        @Test
        fun persistsEmbeddedValueObjects_andRestoresThem() {
            // arrange
            val product = ProductModel.create(
                brandId = 7L,
                name = ProductName("베이직 티셔츠"),
                price = Price(29000),
                likeCount = LikeCount(42),
            )

            // act
            entityManager.persist(product)
            entityManager.flush()
            entityManager.clear()
            val found = entityManager.find(ProductModel::class.java, product.id)

            // assert
            assertAll(
                { assertThat(found.id).isPositive() },
                { assertThat(found.brandId).isEqualTo(7L) },
                { assertThat(found.name).isEqualTo(ProductName("베이직 티셔츠")) },
                { assertThat(found.price).isEqualTo(Price(29000)) },
                { assertThat(found.likeCount).isEqualTo(LikeCount(42)) },
                { assertThat(found.createdAt).isNotNull() },
                { assertThat(found.deletedAt).isNull() },
            )
        }
    }
}
