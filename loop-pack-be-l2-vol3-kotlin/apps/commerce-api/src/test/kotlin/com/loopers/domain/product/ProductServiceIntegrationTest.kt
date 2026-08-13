package com.loopers.domain.product

import com.loopers.domain.support.PageQuery
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ProductServiceIntegrationTest @Autowired constructor(
    private val productService: ProductService,
    private val productRepository: ProductRepository,
    private val databaseCleanUp: DatabaseCleanUp,
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
}
