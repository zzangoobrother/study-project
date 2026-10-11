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

    /**
     * 인덱스는 동작이 아니라 성능의 변경이라 기존 테스트가 잡아주지 않는다.
     * `@Index` 선언을 지워도 모든 테스트가 통과하고, 느려진 것을 아무도 모른 채 배포된다.
     * 이 그룹이 그 회귀를 잡는 유일한 장치다. (2026-09-16 설계 문서 6.4 장, C 안 — 2026-09-20 실측 채택)
     *
     * 주의 - 이것이 통과한다고 dev 이상에 인덱스가 있는 것은 아니다.
     * ddl-auto 가 none 이고 마이그레이션 도구가 없다. (같은 문서 4.2 장)
     */
    @DisplayName("상품 목록 정렬 인덱스는, ")
    @Nested
    inner class SortIndexes {
        @DisplayName("브랜드 필터용과 필터 없는 경로용 두 개가 존재한다.")
        @Test
        fun bothIndexesExist() {
            assertThat(indexNames()).contains("idx_products_brand_del_like", "idx_products_del_like")
        }

        /**
         * 컬럼 순서까지 보는 이유는 이 순서 자체가 측정으로 정해졌기 때문이다.
         * brand_id 가 선두여야 brand_id 단독 인덱스를 접두사로 흡수하고, deleted_at 이 그다음에
         * 등치로 고정돼야 뒤의 정렬 키(like_count, id)가 인덱스 순서 그대로 쓰인다.
         * 컬럼이 빠지면 count 쿼리가 인덱스 온리를 잃고(2026-09-20 실측 4.06ms → 10.6ms, A 안),
         * 순서가 바뀌면 커버링은 남지만 brand_id 단독 인덱스 흡수를 잃는다(B 안).
         * 둘 다 컴파일도 다른 테스트도 통과하므로 이 단언만 잡는다. (설계 문서 6.4 장)
         */
        @DisplayName("두 인덱스의 컬럼 순서가 채택안(C 안)과 일치한다.")
        @Test
        fun columnOrderMatchesAdoptedPlan() {
            // 정렬 방향(Collation)은 단언하지 않는다. Hibernate 6.6.11 이 columnList 의 desc 를
            // 통과시켜 실제로 D 인덱스가 만들어지는 것은 2026-09-21 에 확인했지만, 오름차순이어도
            // Backward index scan 으로 같은 계획이 나오므로(가설 3.5.5) 방향이 바뀌는 것은 회귀가 아니다.
            // 회귀인 것은 컬럼 순서다 - 아래가 그것만 본다.
            assertAll(
                {
                    assertThat(columnsOf("idx_products_brand_del_like"))
                        .containsExactly("brand_id", "deleted_at", "like_count", "id")
                },
                {
                    assertThat(columnsOf("idx_products_del_like"))
                        .containsExactly("deleted_at", "like_count", "id")
                },
            )
        }

        @DisplayName("latest · price_asc 용 네 개가 존재한다.")
        @Test
        fun latestAndPriceIndexesExist() {
            assertThat(indexNames()).contains(
                "idx_products_brand_del_created",
                "idx_products_del_created",
                "idx_products_brand_del_price",
                "idx_products_del_price",
            )
        }

        /**
         * 등치 컬럼(brand_id, deleted_at)이 앞이고 정렬 키가 뒤여야 LIMIT 20 이 인덱스 앞쪽만 읽고 멈춘다.
         * 순서가 바뀌면 filesort 가 돌아오지만 컴파일도 다른 테스트도 통과한다. (2026-10-12 설계 문서 3 장)
         */
        @DisplayName("latest · price_asc 인덱스의 컬럼 순서가 설계와 일치한다.")
        @Test
        fun latestAndPriceColumnOrderMatchesDesign() {
            assertAll(
                {
                    assertThat(columnsOf("idx_products_brand_del_created"))
                        .containsExactly("brand_id", "deleted_at", "created_at", "id")
                },
                {
                    assertThat(columnsOf("idx_products_del_created"))
                        .containsExactly("deleted_at", "created_at", "id")
                },
                {
                    assertThat(columnsOf("idx_products_brand_del_price"))
                        .containsExactly("brand_id", "deleted_at", "price", "id")
                },
                {
                    assertThat(columnsOf("idx_products_del_price"))
                        .containsExactly("deleted_at", "price", "id")
                },
            )
        }

        /**
         * 위 좋아요순은 방향을 단언하지 않지만 price 인덱스는 다르다. price_asc 정렬이 price ASC, id DESC 로
         * 섞여 있어, id 가 ASC 로 바뀌면 Backward scan 으로도 해결되지 않아 동점 구간에서 filesort 가 돌아온다.
         * SHOW INDEX 의 Collation 은 A(오름차순) / D(내림차순)다. (2026-10-12 설계 문서 3 장)
         */
        @DisplayName("price_asc 인덱스는 price 오름차순, id 내림차순으로 섞여 있다.")
        @Test
        fun priceIndexesKeepMixedDirection() {
            assertAll(
                {
                    assertThat(collationsOf("idx_products_brand_del_price"))
                        .containsExactly("A", "A", "A", "D")
                },
                {
                    assertThat(collationsOf("idx_products_del_price"))
                        .containsExactly("A", "A", "D")
                },
            )
        }

        /** 되살아나면 brand_id 선두 인덱스들과 중복이라 모든 쓰기가 쓸모없는 인덱스를 하나 더 갱신한다. (2026-10-12 설계 문서 4 장) */
        @DisplayName("brand_id 단독 인덱스는 존재하지 않는다.")
        @Test
        fun brandIdOnlyIndexIsAbsent() {
            assertThat(indexNames()).doesNotContain("idx_products_brand_id")
        }

        /** SHOW INDEX 의 3 번째 컬럼이 Key_name 이다. */
        private fun indexNames(): Set<String> =
            rows().map { it[2] as String }.toSet()

        /** SHOW INDEX 의 4 번째가 Seq_in_index, 5 번째가 Column_name 이다. Seq_in_index 순 정렬로 컬럼 순서를 복원한다. */
        private fun columnsOf(indexName: String): List<String> =
            rows().filter { it[2] == indexName }
                .sortedBy { (it[3] as Number).toInt() }
                .map { it[4] as String }

        /** SHOW INDEX 의 7 번째가 Collation(A/D)이다. Seq_in_index 순으로 정렬한다. */
        private fun collationsOf(indexName: String): List<String> =
            rows().filter { it[2] == indexName }
                .sortedBy { (it[3] as Number).toInt() }
                .map { it[5].toString() }

        private fun rows(): List<Array<*>> =
            @Suppress("UNCHECKED_CAST")
            (entityManager.createNativeQuery("SHOW INDEX FROM products").resultList as List<Array<*>>)
    }
}
