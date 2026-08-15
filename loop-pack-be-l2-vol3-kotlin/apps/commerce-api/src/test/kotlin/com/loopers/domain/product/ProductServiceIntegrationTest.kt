package com.loopers.domain.product

import com.loopers.domain.support.PageQuery
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest
class ProductServiceIntegrationTest @Autowired constructor(
    private val productService: ProductService,
    private val productRepository: ProductRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val jdbcTemplate: JdbcTemplate,
) {
    private fun saveProducts(vararg products: ProductModel): List<ProductModel> =
        productRepository.saveAll(products.toList())

    private fun product(
        brandId: Long = 1L,
        name: String = "상품",
        price: Long = 10_000,
        likeCount: Long = 0,
    ) = ProductModel.create(
        brandId = brandId,
        name = ProductName(name),
        price = Price(price),
        likeCount = LikeCount(likeCount),
    )

    private fun search(
        brandId: Long? = null,
        sort: ProductSortType = ProductSortType.LATEST,
        page: Int = 0,
        size: Int = 20,
    ) = ProductCriteria.Search(brandId = brandId, sort = sort, pageQuery = PageQuery(page, size))

    /**
     * 단건 저장 단축 헬퍼. Task 8 에서 ProductRepository.save 가 생기지만
     * 이 시점에는 없으므로 기존 saveProducts(= saveAll) 를 그대로 쓴다.
     */
    private fun saveProductFor(
        brandId: Long,
        name: String = "운동화",
        price: Long = 39000,
    ): ProductModel = saveProducts(product(brandId = brandId, name = name, price = price)).first()

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("상품을 단건 조회할 때, ")
    @Nested
    inner class GetProduct {
        @DisplayName("해당 ID 의 상품이 있으면, 상품이 반환된다.")
        @Test
        fun returnsProduct_whenProductExists() {
            // arrange
            val saved = saveProducts(product(name = "베이직 티셔츠")).first()

            // act
            val found = productService.getProduct(saved.id)

            // assert
            assertThat(found?.name).isEqualTo(ProductName("베이직 티셔츠"))
        }

        @DisplayName("해당 ID 의 상품이 없으면, null 이 반환된다.")
        @Test
        fun returnsNull_whenProductDoesNotExist() {
            // assert
            assertThat(productService.getProduct(99999L)).isNull()
        }

        @DisplayName("소프트 삭제된 상품은, null 이 반환된다.")
        @Test
        fun returnsNull_whenProductIsSoftDeleted() {
            // arrange
            val saved = saveProducts(product()).first()
            saved.delete()
            productRepository.saveAll(listOf(saved))

            // assert
            assertThat(productService.getProduct(saved.id)).isNull()
        }
    }

    @DisplayName("상품 목록을 조회할 때, ")
    @Nested
    inner class GetProducts {
        @DisplayName("brandId 를 주면, 해당 브랜드의 상품만 반환된다.")
        @Test
        fun filtersByBrandId() {
            // arrange
            saveProducts(
                product(brandId = 1L, name = "A"),
                product(brandId = 1L, name = "B"),
                product(brandId = 2L, name = "C"),
            )

            // act
            val result = productService.getProducts(search(brandId = 1L))

            // assert
            assertAll(
                { assertThat(result.content).hasSize(2) },
                { assertThat(result.content.map { it.name.value }).containsExactlyInAnyOrder("A", "B") },
                { assertThat(result.totalElements).isEqualTo(2L) },
            )
        }

        @DisplayName("brandId 를 주지 않으면, 전체 상품이 반환된다.")
        @Test
        fun returnsAllProducts_whenBrandIdIsNull() {
            // arrange
            saveProducts(product(brandId = 1L), product(brandId = 2L), product(brandId = 3L))

            // act
            val result = productService.getProducts(search())

            // assert
            assertThat(result.totalElements).isEqualTo(3L)
        }

        @DisplayName("존재하지 않는 brandId 를 주면, 예외 없이 빈 목록이 반환된다.")
        @Test
        fun returnsEmptyResult_whenBrandIdMatchesNothing() {
            // arrange
            saveProducts(product(brandId = 1L))

            // act
            val result = productService.getProducts(search(brandId = 99999L))

            // assert
            assertAll(
                { assertThat(result.content).isEmpty() },
                { assertThat(result.totalElements).isZero() },
                { assertThat(result.totalPages).isZero() },
            )
        }

        @DisplayName("소프트 삭제된 상품은, 목록과 총 개수 모두에서 제외된다.")
        @Test
        fun excludesSoftDeletedProducts_fromContentAndTotalCount() {
            // arrange
            val saved = saveProducts(product(name = "A"), product(name = "B"))
            saved[0].delete()
            productRepository.saveAll(listOf(saved[0]))

            // act
            val result = productService.getProducts(search())

            // assert
            assertAll(
                { assertThat(result.content.map { it.name.value }).containsExactly("B") },
                { assertThat(result.totalElements).isEqualTo(1L) },
            )
        }

        @DisplayName("price_asc 로 정렬하면, 가격 오름차순으로 반환된다.")
        @Test
        fun sortsByPriceAscending() {
            // arrange
            saveProducts(
                product(name = "비쌈", price = 30_000),
                product(name = "쌈", price = 10_000),
                product(name = "중간", price = 20_000),
            )

            // act
            val result = productService.getProducts(search(sort = ProductSortType.PRICE_ASC))

            // assert
            assertThat(result.content.map { it.name.value }).containsExactly("쌈", "중간", "비쌈")
        }

        @DisplayName("likes_desc 로 정렬하면, 좋아요 수 내림차순으로 반환된다.")
        @Test
        fun sortsByLikeCountDescending() {
            // arrange
            saveProducts(
                product(name = "적음", likeCount = 1),
                product(name = "많음", likeCount = 100),
                product(name = "중간", likeCount = 50),
            )

            // act
            val result = productService.getProducts(search(sort = ProductSortType.LIKES_DESC))

            // assert
            assertThat(result.content.map { it.name.value }).containsExactly("많음", "중간", "적음")
        }

        @DisplayName("latest 로 정렬하면, 나중에 저장된 상품이 앞에 온다.")
        @Test
        fun sortsByLatestFirst() {
            // arrange
            val saved = saveProducts(product(name = "A"), product(name = "B"), product(name = "C"))

            // act
            val result = productService.getProducts(search(sort = ProductSortType.LATEST))

            // assert
            // 한 트랜잭션에서 저장하면 createdAt 이 같을 수 있으므로, id 보조 정렬이 순서를 확정한다.
            assertThat(result.content.map { it.id }).containsExactly(saved[2].id, saved[1].id, saved[0].id)
        }

        @DisplayName("정렬 키가 모두 같아도, 페이지 경계에서 중복이나 누락이 생기지 않는다.")
        @Test
        fun doesNotDuplicateOrDropRows_atPageBoundary_whenSortKeysAreIdentical() {
            // arrange
            // 가격이 전부 같으므로 id 보조 정렬이 없으면 페이지 사이의 순서가 보장되지 않는다. (설계 문서 5.5 장)
            val saved = saveProducts(*Array(30) { product(name = "상품${it + 1}", price = 29_000) })

            // act
            val firstPage = productService.getProducts(search(sort = ProductSortType.PRICE_ASC, page = 0, size = 20))
            val secondPage = productService.getProducts(search(sort = ProductSortType.PRICE_ASC, page = 1, size = 20))

            // assert
            val firstIds = firstPage.content.map { it.id }
            val secondIds = secondPage.content.map { it.id }
            assertAll(
                { assertThat(firstIds).hasSize(20) },
                { assertThat(secondIds).hasSize(10) },
                { assertThat(firstIds).doesNotContainAnyElementsOf(secondIds) },
                { assertThat(firstIds + secondIds).containsExactlyInAnyOrderElementsOf(saved.map { it.id }) },
            )
        }

        @DisplayName("마지막 페이지를 넘어선 페이지를 요청하면, content 는 비지만 총 개수는 유지된다.")
        @Test
        fun returnsEmptyContentButKeepsTotalCount_whenPageIsBeyondLast() {
            // arrange
            saveProducts(*Array(25) { product(name = "상품${it + 1}") })

            // act
            val result = productService.getProducts(search(page = 10, size = 20))

            // assert
            assertAll(
                { assertThat(result.content).isEmpty() },
                { assertThat(result.totalElements).isEqualTo(25L) },
                { assertThat(result.totalPages).isEqualTo(2) },
            )
        }

        @DisplayName("페이지와 크기가 응답에 그대로 반영된다.")
        @Test
        fun reflectsRequestedPageAndSize() {
            // arrange
            saveProducts(*Array(25) { product(name = "상품${it + 1}") })

            // act
            val result = productService.getProducts(search(page = 1, size = 10))

            // assert
            assertAll(
                { assertThat(result.page).isEqualTo(1) },
                { assertThat(result.size).isEqualTo(10) },
                { assertThat(result.content).hasSize(10) },
                { assertThat(result.totalElements).isEqualTo(25L) },
                { assertThat(result.totalPages).isEqualTo(3) },
            )
        }
    }

    @DisplayName("삭제 포함으로 상품을 단건 조회할 때, ")
    @Nested
    inner class GetProductIncludingDeleted {
        @DisplayName("살아 있는 상품이 반환된다.")
        @Test
        fun returnsAliveProduct() {
            // arrange
            val saved = saveProductFor(brandId = 1L)

            // act
            val found = productService.getProductIncludingDeleted(saved.id)

            // assert
            assertThat(found?.id).isEqualTo(saved.id)
        }

        @DisplayName("소프트 삭제된 상품도 반환된다.")
        @Test
        fun returnsSoftDeletedProduct() {
            // arrange
            val saved = saveProductFor(brandId = 1L)
            saved.delete()
            productRepository.saveAll(listOf(saved))

            // act
            val found = productService.getProductIncludingDeleted(saved.id)

            // assert
            assertAll(
                { assertThat(found?.id).isEqualTo(saved.id) },
                { assertThat(found?.deletedAt).isNotNull() },
            )
        }

        @DisplayName("존재하지 않는 ID 면, null 이 반환된다.")
        @Test
        fun returnsNull_whenProductDoesNotExist() {
            // act
            val found = productService.getProductIncludingDeleted(99999L)

            // assert
            assertThat(found).isNull()
        }
    }

    @DisplayName("삭제 포함으로 상품 목록을 조회할 때, ")
    @Nested
    inner class GetProductPageIncludingDeleted {
        @DisplayName("삭제된 상품도 content 와 totalElements 양쪽에 포함된다.")
        @Test
        fun includesSoftDeletedProducts() {
            // arrange
            saveProductFor(brandId = 1L, name = "운동화")
            val deleted = saveProductFor(brandId = 1L, name = "러닝화")
            deleted.delete()
            productRepository.saveAll(listOf(deleted))

            // act
            val page = productService.getProductPageIncludingDeleted(
                ProductCriteria.AdminSearch(brandId = null, pageQuery = PageQuery(0, 20)),
            )

            // assert
            assertAll(
                { assertThat(page.content).hasSize(2) },
                { assertThat(page.totalElements).isEqualTo(2L) },
            )
        }

        @DisplayName("brandId 로 필터하면, 해당 브랜드의 상품만 반환된다.")
        @Test
        fun filtersByBrandId() {
            // arrange
            val target = saveProductFor(brandId = 1L, name = "운동화")
            saveProductFor(brandId = 2L, name = "러닝화")

            // act
            val page = productService.getProductPageIncludingDeleted(
                ProductCriteria.AdminSearch(brandId = 1L, pageQuery = PageQuery(0, 20)),
            )

            // assert
            assertThat(page.content.map { it.id }).containsExactly(target.id)
        }

        @DisplayName("최신순으로 정렬된다.")
        @Test
        fun sortsByLatest() {
            // arrange
            val first = saveProductFor(brandId = 1L, name = "운동화")
            val second = saveProductFor(brandId = 1L, name = "러닝화")

            // act
            val page = productService.getProductPageIncludingDeleted(
                ProductCriteria.AdminSearch(brandId = null, pageQuery = PageQuery(0, 20)),
            )

            // assert
            assertThat(page.content.map { it.id }).containsExactly(second.id, first.id)
        }

        /**
         * created_at 이 서로 다르면 1차 정렬 키만으로도 순서가 정해지므로, id DESC 보조 키는
         * created_at 이 충돌할 때만 관찰할 수 있다. 대량 삽입이 같은 시계 틱에 몰리는 상황이 그 실제 사례다.
         * 다만 이 테스트는 증명이 아니라 실용적인 가드다: ORDER BY 에 타이브레이커가 없으면 MySQL 의 행 순서는
         * 정의되지 않으며, 이 테스트는 인덱스 스캔이 자연스럽게 id 오름차순으로 행을 반환한다는 점(단언과는 반대 순서)에 기대고 있다.
         */
        @DisplayName("created_at 이 같으면, id 내림차순으로 정렬된다.")
        @Test
        fun breaksCreatedAtTieByIdDesc() {
            // arrange
            val first = saveProductFor(brandId = 1L, name = "A")
            val second = saveProductFor(brandId = 1L, name = "B")
            val third = saveProductFor(brandId = 1L, name = "C")
            jdbcTemplate.update("UPDATE products SET created_at = ?", java.sql.Timestamp.valueOf("2026-01-01 00:00:00"))

            // act
            val page = productService.getProductPageIncludingDeleted(
                ProductCriteria.AdminSearch(brandId = null, pageQuery = PageQuery(0, 20)),
            )

            // assert
            assertThat(page.content.map { it.id }).containsExactly(third.id, second.id, first.id)
        }

        @DisplayName("존재하지 않는 brandId 로 필터하면, 빈 목록이 반환된다.")
        @Test
        fun returnsEmpty_whenBrandIdMatchesNothing() {
            // arrange
            saveProductFor(brandId = 1L)

            // act
            val page = productService.getProductPageIncludingDeleted(
                ProductCriteria.AdminSearch(brandId = 99999L, pageQuery = PageQuery(0, 20)),
            )

            // assert
            assertAll(
                { assertThat(page.content).isEmpty() },
                { assertThat(page.totalElements).isEqualTo(0L) },
            )
        }
    }

    /**
     * 공개 조회가 어드민 변경의 영향을 받지 않는지 확인한다.
     * QueryDSL 쿼리 본문을 execute 로 추출하면서 삭제 필터가 빠지는 회귀가 가장 위험하다.
     */
    @DisplayName("QueryDSL 재구성 이후에도 공개 목록 조회는, ")
    @Nested
    inner class PublicSearchRegression {
        @DisplayName("소프트 삭제된 상품을 여전히 제외한다.")
        @Test
        fun stillExcludesSoftDeletedProducts() {
            // arrange
            saveProductFor(brandId = 1L, name = "운동화")
            val deleted = saveProductFor(brandId = 1L, name = "러닝화")
            deleted.delete()
            productRepository.saveAll(listOf(deleted))

            // act
            val page = productService.getProducts(
                ProductCriteria.Search(brandId = null, sort = ProductSortType.LATEST, pageQuery = PageQuery(0, 20)),
            )

            // assert
            assertAll(
                { assertThat(page.content).hasSize(1) },
                { assertThat(page.totalElements).isEqualTo(1L) },
            )
        }
    }

    @DisplayName("상품을 등록할 때, ")
    @Nested
    inner class Register {
        @DisplayName("상품이 저장되고 좋아요 수는 0 이 된다.")
        @Test
        fun savesProductWithZeroLikeCount() {
            // act
            val registered = productService.register(
                ProductCommand.Register(brandId = 1L, name = ProductName("운동화"), price = Price(39000)),
            )

            // assert
            assertAll(
                { assertThat(registered.id).isPositive() },
                { assertThat(registered.likeCount).isEqualTo(LikeCount.ZERO) },
                { assertThat(registered.brandId).isEqualTo(1L) },
            )
        }

        @DisplayName("등록한 상품을 다시 조회할 수 있다.")
        @Test
        fun registeredProductIsRetrievable() {
            // act
            val registered = productService.register(
                ProductCommand.Register(brandId = 1L, name = ProductName("운동화"), price = Price(39000)),
            )

            // assert
            assertThat(productService.getProduct(registered.id)?.name).isEqualTo(ProductName("운동화"))
        }
    }

    @DisplayName("상품을 수정할 때, ")
    @Nested
    inner class Change {
        @DisplayName("이름과 가격이 교체되고 브랜드는 유지된다.")
        @Test
        fun changesNameAndPriceOnly() {
            // arrange
            val saved = saveProductFor(brandId = 1L)

            // act
            productService.change(ProductCommand.Change(saved.id, ProductName("러닝화"), Price(59000)))

            // assert
            val found = productService.getProduct(saved.id)
            assertAll(
                { assertThat(found?.name).isEqualTo(ProductName("러닝화")) },
                { assertThat(found?.price).isEqualTo(Price(59000)) },
                { assertThat(found?.brandId).isEqualTo(1L) },
            )
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 를 던진다.")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            // act & assert
            assertThatThrownBy {
                productService.change(ProductCommand.Change(99999L, ProductName("러닝화"), Price(59000)))
            }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("소프트 삭제된 상품이면, CONFLICT 를 던진다.")
        @Test
        fun throwsConflict_whenProductIsSoftDeleted() {
            // arrange
            val saved = saveProductFor(brandId = 1L)
            saved.delete()
            productRepository.saveAll(listOf(saved))

            // act & assert
            assertThatThrownBy {
                productService.change(ProductCommand.Change(saved.id, ProductName("러닝화"), Price(59000)))
            }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.CONFLICT)
        }
    }

    @DisplayName("상품을 삭제할 때, ")
    @Nested
    inner class Delete {
        @DisplayName("deletedAt 이 찍히고 공개 조회에서 사라진다.")
        @Test
        fun softDeletesProduct() {
            // arrange
            val saved = saveProductFor(brandId = 1L)

            // act
            productService.delete(saved.id)

            // assert
            assertAll(
                { assertThat(productService.getProduct(saved.id)).isNull() },
                { assertThat(productService.getProductIncludingDeleted(saved.id)?.deletedAt).isNotNull() },
            )
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 를 던진다.")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            // act & assert
            assertThatThrownBy { productService.delete(99999L) }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("이미 삭제된 상품을 다시 삭제해도, 예외 없이 deletedAt 이 유지된다.")
        @Test
        fun isIdempotent() {
            // arrange
            val saved = saveProductFor(brandId = 1L)
            productService.delete(saved.id)
            val firstDeletedAt = productService.getProductIncludingDeleted(saved.id)?.deletedAt

            // act
            productService.delete(saved.id)

            // assert
            assertThat(productService.getProductIncludingDeleted(saved.id)?.deletedAt).isEqualTo(firstDeletedAt)
        }
    }

    @DisplayName("브랜드의 상품을 일괄 삭제할 때, ")
    @Nested
    inner class DeleteAllByBrandId {
        @DisplayName("해당 브랜드의 상품이 전부 소프트 삭제된다.")
        @Test
        fun softDeletesAllProductsOfBrand() {
            // arrange
            val first = saveProductFor(brandId = 1L, name = "운동화")
            val second = saveProductFor(brandId = 1L, name = "러닝화")

            // act
            productService.deleteAllByBrandId(1L)

            // assert
            assertAll(
                { assertThat(productService.getProduct(first.id)).isNull() },
                { assertThat(productService.getProduct(second.id)).isNull() },
            )
        }

        /**
         * 이 테스트가 이 메서드에서 가장 중요하다.
         * where brand_id = ? 를 빠뜨리면 전체 상품이 삭제되는데,
         * 대상 브랜드의 상품만 확인하는 테스트는 그 버그를 통과시킨다.
         * 지워야 할 것이 지워졌는지와 지우지 말아야 할 것이 남았는지를 둘 다 봐야 한다.
         */
        @DisplayName("다른 브랜드의 상품은 삭제되지 않는다.")
        @Test
        fun doesNotTouchOtherBrandsProducts() {
            // arrange
            val target = saveProductFor(brandId = 1L, name = "운동화")
            val untouched = saveProductFor(brandId = 2L, name = "러닝화")

            // act
            productService.deleteAllByBrandId(1L)

            // assert
            assertAll(
                { assertThat(productService.getProduct(target.id)).isNull() },
                { assertThat(productService.getProduct(untouched.id)).isNotNull() },
            )
        }

        @DisplayName("상품이 없는 브랜드여도, 예외 없이 통과한다.")
        @Test
        fun doesNothing_whenBrandHasNoProducts() {
            // act & assert
            productService.deleteAllByBrandId(99999L)
        }

        @DisplayName("이미 삭제된 상품이 섞여 있어도, 그 deletedAt 은 갱신되지 않는다.")
        @Test
        fun keepsDeletedAtOfAlreadyDeletedProducts() {
            // arrange
            val alreadyDeleted = saveProductFor(brandId = 1L, name = "운동화")
            alreadyDeleted.delete()
            productRepository.saveAll(listOf(alreadyDeleted))
            val firstDeletedAt = productService.getProductIncludingDeleted(alreadyDeleted.id)?.deletedAt

            // act
            productService.deleteAllByBrandId(1L)

            // assert
            assertThat(productService.getProductIncludingDeleted(alreadyDeleted.id)?.deletedAt).isEqualTo(firstDeletedAt)
        }
    }
}
