package com.loopers.interfaces.api.admin

import com.loopers.domain.brand.BrandDescription
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import com.loopers.interfaces.api.admin.product.ProductAdminV1Dto
import com.loopers.support.auth.AdminAuthInterceptor
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductAdminV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT = "/api-admin/v1/products"
        private const val ADMIN_ID = "admin"
        private const val ADMIN_PW = "admin1234"
    }

    private val productType = object : ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductResponse>>() {}
    private val pageType =
        object : ParameterizedTypeReference<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>>() {}

    private fun adminHeaders(): HttpHeaders = HttpHeaders().apply {
        set(AdminAuthInterceptor.HEADER_LDAP_ID, ADMIN_ID)
        set(AdminAuthInterceptor.HEADER_LDAP_PW, ADMIN_PW)
        contentType = MediaType.APPLICATION_JSON
    }

    private fun saveBrand(name: String = "루퍼스"): BrandModel =
        brandRepository.save(BrandModel.create(BrandName(name), BrandDescription("일상을 조금 낫게")))

    private fun saveProduct(brandId: Long, name: String = "운동화", price: Long = 39000): ProductModel =
        productRepository.save(ProductModel.create(brandId = brandId, name = ProductName(name), price = Price(price)))

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("GET /api-admin/v1/products/{productId}")
    @Nested
    inner class GetProduct {
        @DisplayName("상품과 브랜드 요약이 함께 반환된다.")
        @Test
        fun returnsProductWithBrand() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${product.id}",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                productType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.id).isEqualTo(product.id) },
                { assertThat(response.body?.data?.name).isEqualTo("운동화") },
                { assertThat(response.body?.data?.price).isEqualTo(39000L) },
                { assertThat(response.body?.data?.likeCount).isEqualTo(0L) },
                { assertThat(response.body?.data?.brand?.id).isEqualTo(brand.id) },
                { assertThat(response.body?.data?.brand?.deleted).isFalse() },
                { assertThat(response.body?.data?.deleted).isFalse() },
            )
        }

        @DisplayName("브랜드가 삭제됐어도, 브랜드 요약이 채워지고 brand.deleted 가 true 다.")
        @Test
        fun fillsDeletedBrand() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)
            brand.delete()
            brandRepository.save(brand)

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${product.id}",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                productType,
            )

            // assert
            assertAll(
                { assertThat(response.body?.data?.brand?.name).isEqualTo("루퍼스") },
                { assertThat(response.body?.data?.brand?.deleted).isTrue() },
            )
        }

        @DisplayName("삭제된 상품도 200 으로 반환되고 deleted 가 true 다.")
        @Test
        fun returnsDeletedProduct() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)
            product.delete()
            productRepository.save(product)

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${product.id}",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                productType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.deleted).isTrue() },
            )
        }

        @DisplayName("존재하지 않는 상품이면, 404 Not Found 를 반환한다.")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/99999",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                productType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("인증 헤더가 없으면, 401 Unauthorized 를 반환한다.")
        @Test
        fun returnsUnauthorized_whenHeadersAreMissing() {
            // act
            val response = testRestTemplate.exchange("$ENDPOINT/1", HttpMethod.GET, null, productType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @DisplayName("GET /api-admin/v1/products")
    @Nested
    inner class GetProducts {
        @DisplayName("삭제된 상품도 포함해 최신순으로 반환한다.")
        @Test
        fun includesDeletedProducts() {
            // arrange
            val brand = saveBrand()
            val alive = saveProduct(brand.id, name = "운동화")
            val deleted = saveProduct(brand.id, name = "러닝화")
            deleted.delete()
            productRepository.save(deleted)

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                pageType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content?.map { it.id }).containsExactly(deleted.id, alive.id) },
                { assertThat(response.body?.data?.totalElements).isEqualTo(2L) },
            )
        }

        @DisplayName("brandId 로 필터하면, 해당 브랜드의 상품만 반환된다.")
        @Test
        fun filtersByBrandId() {
            // arrange
            val target = saveBrand(name = "루퍼스")
            val other = saveBrand(name = "몬드리안")
            val targetProduct = saveProduct(target.id, name = "운동화")
            saveProduct(other.id, name = "러닝화")

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT?brandId=${target.id}",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                pageType,
            )

            // assert
            assertThat(response.body?.data?.content?.map { it.id }).containsExactly(targetProduct.id)
        }

        /**
         * brandId 는 리소스 식별자가 아니라 필터 조건이므로 404 가 아니다.
         * 공개 API 와 같은 판단이다.
         */
        @DisplayName("존재하지 않는 brandId 로 필터하면, 200 과 빈 목록을 반환한다.")
        @Test
        fun returnsEmptyList_whenBrandIdMatchesNothing() {
            // arrange
            val brand = saveBrand()
            saveProduct(brand.id)

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT?brandId=99999",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                pageType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content).isEmpty() },
                { assertThat(response.body?.data?.totalElements).isEqualTo(0L) },
            )
        }

        @DisplayName("brandId 가 숫자가 아니면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenBrandIdIsNotNumeric() {
            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT?brandId=abc",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                pageType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("size 가 상한을 넘으면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenSizeExceedsMax() {
            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT?size=101",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                pageType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }
}
