package com.loopers.interfaces.api

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.LikeCount
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.interfaces.api.product.ProductV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT_PRODUCT = "/api/v1/products"
    }

    private val listResponseType =
        object : ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>>() {}
    private val detailResponseType =
        object : ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>>() {}

    private fun saveBrand(name: String = "루퍼스"): BrandModel =
        brandRepository.save(BrandModel.create(BrandName(name)))

    private fun saveProducts(vararg products: ProductModel): List<ProductModel> =
        productRepository.saveAll(products.toList())

    private fun product(brandId: Long, name: String = "상품", price: Long = 10_000, likeCount: Long = 0) =
        ProductModel.create(
            brandId = brandId,
            name = ProductName(name),
            price = Price(price),
            likeCount = LikeCount(likeCount),
        )

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    inner class GetProducts {
        @DisplayName("파라미터 없이 조회하면, 최신순 0페이지 20건이 반환된다.")
        @Test
        fun appliesDefaults_whenNoParameterIsGiven() {
            // arrange
            val brand = saveBrand()
            val saved = saveProducts(*Array(25) { product(brandId = brand.id, name = "상품${it + 1}") })

            // act
            val response = testRestTemplate.exchange(ENDPOINT_PRODUCT, HttpMethod.GET, null, listResponseType)

            // assert
            val data = response.body?.data
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(data?.page).isEqualTo(0) },
                { assertThat(data?.size).isEqualTo(20) },
                { assertThat(data?.content).hasSize(20) },
                { assertThat(data?.totalElements).isEqualTo(25L) },
                { assertThat(data?.totalPages).isEqualTo(2) },
                { assertThat(data?.content?.first()?.id).isEqualTo(saved.last().id) },
            )
        }

        @DisplayName("상품에 브랜드 요약 정보가 함께 반환된다.")
        @Test
        fun includesBrandSummary() {
            // arrange
            val brand = saveBrand("루퍼스")
            saveProducts(product(brandId = brand.id, name = "베이직 티셔츠", price = 29_000, likeCount = 42))

            // act
            val response = testRestTemplate.exchange(ENDPOINT_PRODUCT, HttpMethod.GET, null, listResponseType)

            // assert
            val item = response.body?.data?.content?.first()
            assertAll(
                { assertThat(item?.name).isEqualTo("베이직 티셔츠") },
                { assertThat(item?.price).isEqualTo(29_000L) },
                { assertThat(item?.likeCount).isEqualTo(42L) },
                { assertThat(item?.brand?.id).isEqualTo(brand.id) },
                { assertThat(item?.brand?.name).isEqualTo("루퍼스") },
            )
        }

        @DisplayName("brandId 로 필터하면, 해당 브랜드의 상품만 반환된다.")
        @Test
        fun filtersByBrandId() {
            // arrange
            val first = saveBrand("루퍼스")
            val second = saveBrand("몬드리안")
            saveProducts(
                product(brandId = first.id, name = "A"),
                product(brandId = second.id, name = "B"),
            )

            // act
            val response =
                testRestTemplate.exchange("$ENDPOINT_PRODUCT?brandId=${first.id}", HttpMethod.GET, null, listResponseType)

            // assert
            assertAll(
                { assertThat(response.body?.data?.totalElements).isEqualTo(1L) },
                { assertThat(response.body?.data?.content?.first()?.name).isEqualTo("A") },
            )
        }

        @DisplayName("존재하지 않는 brandId 로 필터하면, 404 가 아니라 200 과 빈 목록을 반환한다.")
        @Test
        fun returnsEmptyList_whenBrandIdMatchesNothing() {
            // arrange
            val brand = saveBrand()
            saveProducts(product(brandId = brand.id))

            // act
            val response =
                testRestTemplate.exchange("$ENDPOINT_PRODUCT?brandId=99999", HttpMethod.GET, null, listResponseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content).isEmpty() },
                { assertThat(response.body?.data?.totalElements).isZero() },
                { assertThat(response.body?.data?.totalPages).isZero() },
            )
        }

        @DisplayName("sort=price_asc 로 조회하면, 가격 오름차순으로 반환된다.")
        @Test
        fun sortsByPriceAscending() {
            // arrange
            val brand = saveBrand()
            saveProducts(
                product(brandId = brand.id, name = "비쌈", price = 30_000),
                product(brandId = brand.id, name = "쌈", price = 10_000),
            )

            // act
            val response =
                testRestTemplate.exchange("$ENDPOINT_PRODUCT?sort=price_asc", HttpMethod.GET, null, listResponseType)

            // assert
            assertThat(response.body?.data?.content?.map { it.name }).containsExactly("쌈", "비쌈")
        }

        @DisplayName("sort=likes_desc 로 조회하면, 좋아요 수 내림차순으로 반환된다.")
        @Test
        fun sortsByLikeCountDescending() {
            // arrange
            val brand = saveBrand()
            saveProducts(
                product(brandId = brand.id, name = "적음", likeCount = 1),
                product(brandId = brand.id, name = "많음", likeCount = 100),
            )

            // act
            val response =
                testRestTemplate.exchange("$ENDPOINT_PRODUCT?sort=likes_desc", HttpMethod.GET, null, listResponseType)

            // assert
            assertThat(response.body?.data?.content?.map { it.name }).containsExactly("많음", "적음")
        }

        @DisplayName("지원하지 않는 sort 값이면, 400 Bad Request 를 반환한다.")
        @ParameterizedTest
        @ValueSource(strings = ["price_desc", "LATEST", "weird"])
        fun returnsBadRequest_whenSortIsNotSupported(sort: String) {
            // act
            val response =
                testRestTemplate.exchange("$ENDPOINT_PRODUCT?sort=$sort", HttpMethod.GET, null, listResponseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("page 나 size 가 허용 범위를 벗어나면, 400 Bad Request 를 반환한다.")
        @ParameterizedTest
        @ValueSource(strings = ["page=-1", "size=0", "size=101", "size=-5"])
        fun returnsBadRequest_whenPagingParameterIsOutOfRange(query: String) {
            // act
            val response = testRestTemplate.exchange("$ENDPOINT_PRODUCT?$query", HttpMethod.GET, null, listResponseType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("page 나 size 나 brandId 가 숫자가 아니면, 500 이 아니라 400 Bad Request 를 반환한다.")
        @ParameterizedTest
        @ValueSource(strings = ["page=abc", "size=abc", "brandId=abc"])
        fun returnsBadRequest_whenParameterIsNotNumeric(query: String) {
            // act
            val response = testRestTemplate.exchange("$ENDPOINT_PRODUCT?$query", HttpMethod.GET, null, listResponseType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("브랜드가 소프트 삭제된 상품은, brand 가 null 인 채로 목록에 남는다.")
        @Test
        fun returnsNullBrand_whenBrandIsSoftDeleted() {
            // arrange
            val brand = saveBrand()
            saveProducts(product(brandId = brand.id, name = "고아 상품"))
            brand.delete()
            brandRepository.save(brand)

            // act
            val response = testRestTemplate.exchange(ENDPOINT_PRODUCT, HttpMethod.GET, null, listResponseType)

            // assert
            assertAll(
                { assertThat(response.body?.data?.totalElements).isEqualTo(1L) },
                { assertThat(response.body?.data?.content?.first()?.name).isEqualTo("고아 상품") },
                { assertThat(response.body?.data?.content?.first()?.brand).isNull() },
            )
        }
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    inner class GetProduct {
        @DisplayName("존재하는 상품을 조회하면, 상품 정보를 반환한다.")
        @Test
        fun returnsProduct_whenProductExists() {
            // arrange
            val brand = saveBrand("루퍼스")
            val saved =
                saveProducts(product(brandId = brand.id, name = "베이직 티셔츠", price = 29_000, likeCount = 42)).first()

            // act
            val response =
                testRestTemplate.exchange("$ENDPOINT_PRODUCT/${saved.id}", HttpMethod.GET, null, detailResponseType)

            // assert
            val data = response.body?.data
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(data?.id).isEqualTo(saved.id) },
                { assertThat(data?.name).isEqualTo("베이직 티셔츠") },
                { assertThat(data?.price).isEqualTo(29_000L) },
                { assertThat(data?.likeCount).isEqualTo(42L) },
                { assertThat(data?.brand?.name).isEqualTo("루퍼스") },
            )
        }

        @DisplayName("존재하지 않는 상품을 조회하면, 404 Not Found 를 반환한다.")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            // act
            val response =
                testRestTemplate.exchange("$ENDPOINT_PRODUCT/99999", HttpMethod.GET, null, detailResponseType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("소프트 삭제된 상품을 조회하면, 404 Not Found 를 반환한다.")
        @Test
        fun returnsNotFound_whenProductIsSoftDeleted() {
            // arrange
            val brand = saveBrand()
            val saved = saveProducts(product(brandId = brand.id)).first()
            saved.delete()
            productRepository.saveAll(listOf(saved))

            // act
            val response =
                testRestTemplate.exchange("$ENDPOINT_PRODUCT/${saved.id}", HttpMethod.GET, null, detailResponseType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("상품 ID 가 숫자가 아니면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenProductIdIsNotNumeric() {
            // act
            val response = testRestTemplate.exchange("$ENDPOINT_PRODUCT/abc", HttpMethod.GET, null, detailResponseType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }
}
