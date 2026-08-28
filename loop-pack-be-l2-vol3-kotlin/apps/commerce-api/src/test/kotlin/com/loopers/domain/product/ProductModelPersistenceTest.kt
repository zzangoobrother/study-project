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
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
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

    /**
     * stock 과 like_count 의 음수를 막는 최후 방어선이 스키마에 살아 있는지 확인한다.
     *
     * 네이티브 UPDATE 로 찌르는 것이 이 테스트의 핵심이다. 값 객체(Stock·LikeCount)와 차감 쿼리의
     * WHERE 절을 모두 우회해야 CHECK 제약만 남고, 그래야 이 단언이 제약 자체를 보는 것이 된다.
     * ProductModel 의 Check 애노테이션이 사라지면 UPDATE 가 성공해 여기서 깨진다.
     *
     * 컬럼을 파라미터로 받는 이유는 제약이 늘어날 때 케이스 한 줄만 추가하면 되게 하기 위해서다.
     * 두 컬럼이 같은 이유로 같은 제약을 갖고 있으므로 테스트도 같은 모양이어야 한다.
     */
    @DisplayName("컬럼을 음수로 만드는 네이티브 UPDATE 는, ")
    @Nested
    inner class NonNegativeCheckConstraints {
        @DisplayName("CHECK 제약에 걸려 실패한다.")
        @ParameterizedTest(name = "{0}")
        @CsvSource(
            "stock, ck_products_stock_non_negative",
            "like_count, ck_products_like_count_non_negative",
        )
        @Transactional
        fun rejectsNegativeValue(column: String, constraintName: String) {
            // arrange
            val product = persistProduct()

            // act
            val result = assertThrows<Exception> { update(column, -1, product.id) }

            // assert — 아무 예외나 통과시키지 않도록 제약 이름이 예외 사슬에 나타나는지까지 본다
            assertThat(messageChain(result)).contains(constraintName)
        }

        @DisplayName("0 으로 만드는 UPDATE 는 통과한다. 품절과 좋아요 0 은 정상 상태다.")
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = ["stock", "like_count"])
        @Transactional
        fun allowsZeroValue(column: String) {
            // arrange
            val product = persistProduct()

            // act
            val affected = update(column, 0, product.id)

            // assert
            assertThat(affected).isEqualTo(1)
        }

        private fun persistProduct(): ProductModel =
            ProductModel.create(
                brandId = 1L,
                name = ProductName("운동화"),
                price = Price(10_000),
                likeCount = LikeCount(1),
                stock = Stock(1),
            ).also {
                entityManager.persist(it)
                entityManager.flush()
            }

        /** 컬럼명은 테스트가 소유한 상수뿐이라 문자열로 끼워도 외부 입력이 닿지 않는다. */
        private fun update(column: String, value: Long, id: Long): Int =
            entityManager
                .createNativeQuery("UPDATE products SET $column = :value WHERE id = :id")
                .setParameter("value", value)
                .setParameter("id", id)
                .executeUpdate()

        private fun messageChain(e: Throwable): String =
            generateSequence(e) { it.cause }.mapNotNull { it.message }.joinToString(" | ")
    }
}
