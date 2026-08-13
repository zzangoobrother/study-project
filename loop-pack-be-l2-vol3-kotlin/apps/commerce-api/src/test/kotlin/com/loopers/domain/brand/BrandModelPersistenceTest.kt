package com.loopers.domain.brand

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
class BrandModelPersistenceTest @Autowired constructor(
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("브랜드를 저장하면, ")
    @Nested
    inner class Persist {
        @DisplayName("값 객체가 컬럼으로 풀려 저장되고 다시 읽을 때 복원된다.")
        @Transactional
        @Test
        fun persistsEmbeddedValueObjects_andRestoresThem() {
            // arrange
            val brand = BrandModel.create(BrandName("루퍼스"), BrandDescription("일상을 조금 낫게"))

            // act
            entityManager.persist(brand)
            entityManager.flush()
            entityManager.clear()
            val found = entityManager.find(BrandModel::class.java, brand.id)

            // assert
            assertAll(
                { assertThat(found.id).isPositive() },
                { assertThat(found.name).isEqualTo(BrandName("루퍼스")) },
                { assertThat(found.description).isEqualTo(BrandDescription("일상을 조금 낫게")) },
                { assertThat(found.createdAt).isNotNull() },
                { assertThat(found.updatedAt).isNotNull() },
                { assertThat(found.deletedAt).isNull() },
            )
        }

        @DisplayName("설명을 생략하면, 빈 문자열로 저장된다.")
        @Transactional
        @Test
        fun persistsEmptyDescription_whenDescriptionIsOmitted() {
            // arrange
            val brand = BrandModel.create(BrandName("하바나"))

            // act
            entityManager.persist(brand)
            entityManager.flush()
            entityManager.clear()
            val found = entityManager.find(BrandModel::class.java, brand.id)

            // assert
            assertThat(found.description).isEqualTo(BrandDescription.EMPTY)
        }
    }

    @DisplayName("브랜드를 삭제하면, ")
    @Nested
    inner class Delete {
        @DisplayName("행이 지워지지 않고 deletedAt 만 채워진다.")
        @Transactional
        @Test
        fun marksDeletedAt_insteadOfRemovingRow() {
            // arrange
            val brand = BrandModel.create(BrandName("루퍼스"))
            entityManager.persist(brand)
            entityManager.flush()

            // act
            brand.delete()
            entityManager.flush()
            entityManager.clear()
            val found = entityManager.find(BrandModel::class.java, brand.id)

            // assert
            assertThat(found.deletedAt).isNotNull()
        }
    }
}
