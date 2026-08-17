package com.loopers.interfaces.api.admin

import com.loopers.domain.brand.BrandDescription
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductService
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import com.loopers.interfaces.api.admin.brand.BrandAdminV1Dto
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
class BrandAdminV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val productService: ProductService,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT = "/api-admin/v1/brands"

        /** application.yml 의 local, test 프로필 섹션에 설정된 스텁 자격 증명이다. */
        private const val ADMIN_ID = "admin"
        private const val ADMIN_PW = "admin1234"
    }

    private val brandType = object : ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandResponse>>() {}
    private val pageType =
        object : ParameterizedTypeReference<ApiResponse<PageResponse<BrandAdminV1Dto.BrandResponse>>>() {}

    private fun adminHeaders(id: String = ADMIN_ID, password: String = ADMIN_PW): HttpHeaders =
        HttpHeaders().apply {
            set(AdminAuthInterceptor.HEADER_LDAP_ID, id)
            set(AdminAuthInterceptor.HEADER_LDAP_PW, password)
            contentType = MediaType.APPLICATION_JSON
        }

    private fun saveBrand(name: String = "루퍼스", description: String = "일상을 조금 낫게"): BrandModel =
        brandRepository.save(BrandModel.create(BrandName(name), BrandDescription(description)))

    private fun saveProduct(brandId: Long, name: String = "운동화"): ProductModel =
        productRepository.save(ProductModel.create(brandId = brandId, name = ProductName(name), price = Price(39000)))

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    /**
     * 인터셉터가 /api-admin 하위 경로에 실제로 등록됐는지 확인하는 첫 지점이다.
     * WebConfig 의 경로 패턴이 틀리면 이 클래스가 통째로 실패한다.
     */
    @DisplayName("어드민 API 인증")
    @Nested
    inner class Authentication {
        @DisplayName("인증 헤더가 없으면, 401 Unauthorized 를 반환한다.")
        @Test
        fun returnsUnauthorized_whenHeadersAreMissing() {
            // act
            val response = testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, null, brandType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("자격 증명이 틀리면, 401 Unauthorized 를 반환한다.")
        @Test
        fun returnsUnauthorized_whenCredentialIsInvalid() {
            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders(password = "wrong-password")),
                brandType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        /**
         * 응답 타입을 String 으로 받는 이유는, 공개 API 의 BrandV1Dto.BrandResponse 에는
         * deleted / createdAt / updatedAt 이 없어서 어드민 DTO 로 역직렬화하면
         * Kotlin non-null 파라미터 누락으로 예외가 나기 때문이다. 여기서 볼 것은 상태 코드뿐이다.
         */
        @DisplayName("공개 API 는 인증 헤더 없이도 통과한다.")
        @Test
        fun publicApiIsNotIntercepted() {
            // arrange
            val brand = saveBrand()

            // act
            val response = testRestTemplate.exchange(
                "/api/v1/brands/${brand.id}",
                HttpMethod.GET,
                null,
                String::class.java,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        }
    }

    @DisplayName("GET /api-admin/v1/brands/{brandId}")
    @Nested
    inner class GetBrand {
        @DisplayName("살아 있는 브랜드를 조회하면, deleted 가 false 로 반환된다.")
        @Test
        fun returnsBrand_whenBrandIsAlive() {
            // arrange
            val brand = saveBrand()

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${brand.id}",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                brandType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.id).isEqualTo(brand.id) },
                { assertThat(response.body?.data?.name).isEqualTo("루퍼스") },
                { assertThat(response.body?.data?.deleted).isFalse() },
                { assertThat(response.body?.data?.deletedAt).isNull() },
                { assertThat(response.body?.data?.createdAt).isNotNull() },
            )
        }

        /**
         * 위 테스트의 createdAt 단언은 response.body 가 이미 역직렬화된 뒤라 와이어 형식과 무관하게 통과한다.
         * 형식을 결정하는 것은 이 모듈이 아니라 supports:jackson 의 전역 설정과 Boot 기본값이라,
         * 여기서 원시 응답 본문을 직접 확인해 형식을 못 박아 둔다.
         */
        @DisplayName("타임스탬프는 오프셋을 포함한 ISO-8601 문자열로 직렬화된다.")
        @Test
        fun serializesTimestampsAsIso8601() {
            // arrange
            val brand = saveBrand()

            // act
            val raw = testRestTemplate.exchange(
                "$ENDPOINT/${brand.id}",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                String::class.java,
            ).body!!

            // assert
            // 배열(epoch) 직렬화로 바뀌면 이 정규식이 실패한다. 형식은 supports:jackson 의 전역 설정이 결정하므로
            // 이 모듈 밖의 변경으로 조용히 깨질 수 있고, 그래서 어드민 쪽에서 한 번 못 박아 둔다.
            // ZonedDateTime.now() 가 JVM 기본 타임존을 따르므로(BaseEntity), 오프셋은 UTC 인 Z 로도,
            // +09:00 같은 숫자 오프셋으로도 나올 수 있다 — 둘 다 유효한 ISO-8601 이므로 둘 다 허용한다.
            assertThat(raw).containsPattern(""""createdAt":"\d{4}-\d{2}-\d{2}T[\d:.]+(Z|[+\-]\d{2}:\d{2})"""")
        }

        /**
         * 공개 API 는 같은 요청에 404 를 반환한다. 어드민만의 계약이다.
         */
        @DisplayName("삭제된 브랜드를 조회하면, 200 과 함께 deleted 가 true 로 반환된다.")
        @Test
        fun returnsDeletedBrand() {
            // arrange
            val brand = saveBrand()
            brand.delete()
            brandRepository.save(brand)

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${brand.id}",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                brandType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.deleted).isTrue() },
                { assertThat(response.body?.data?.deletedAt).isNotNull() },
            )
        }

        @DisplayName("존재하지 않는 브랜드를 조회하면, 404 Not Found 를 반환한다.")
        @Test
        fun returnsNotFound_whenBrandDoesNotExist() {
            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/99999",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                brandType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("브랜드 ID 가 숫자가 아니면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenBrandIdIsNotNumeric() {
            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/abc",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                brandType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @DisplayName("GET /api-admin/v1/brands")
    @Nested
    inner class GetBrands {
        @DisplayName("삭제된 브랜드도 포함해 최신순으로 반환한다.")
        @Test
        fun returnsAllBrandsIncludingDeleted() {
            // arrange
            val first = saveBrand(name = "루퍼스")
            val second = saveBrand(name = "몬드리안")
            second.delete()
            brandRepository.save(second)

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
                { assertThat(response.body?.data?.content?.map { it.id }).containsExactly(second.id, first.id) },
                { assertThat(response.body?.data?.totalElements).isEqualTo(2L) },
                { assertThat(response.body?.data?.page).isEqualTo(0) },
                { assertThat(response.body?.data?.size).isEqualTo(20) },
            )
        }

        @DisplayName("page 와 size 를 지정하면, 해당 페이지가 반환된다.")
        @Test
        fun respectsPageAndSize() {
            // arrange
            repeat(3) { saveBrand(name = "브랜드$it") }

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT?page=1&size=2",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                pageType,
            )

            // assert
            assertAll(
                { assertThat(response.body?.data?.content).hasSize(1) },
                { assertThat(response.body?.data?.totalPages).isEqualTo(2) },
            )
        }

        @DisplayName("page 가 음수면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenPageIsNegative() {
            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT?page=-1",
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

    @DisplayName("POST /api-admin/v1/brands")
    @Nested
    inner class RegisterBrand {
        @DisplayName("브랜드를 등록하면, 200 과 함께 등록된 정보를 반환한다.")
        @Test
        fun registersBrand() {
            // arrange
            val body = mapOf("name" to "루퍼스", "description" to "일상을 조금 낫게")

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                HttpEntity(body, adminHeaders()),
                brandType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.id).isNotNull() },
                { assertThat(response.body?.data?.name).isEqualTo("루퍼스") },
                { assertThat(response.body?.data?.description).isEqualTo("일상을 조금 낫게") },
                { assertThat(response.body?.data?.deleted).isFalse() },
            )
        }

        @DisplayName("description 을 생략하면, 빈 문자열로 등록된다.")
        @Test
        fun registersWithEmptyDescription_whenDescriptionIsOmitted() {
            // arrange
            val body = mapOf("name" to "하바나")

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                HttpEntity(body, adminHeaders()),
                brandType,
            )

            // assert
            assertThat(response.body?.data?.description).isEmpty()
        }

        @DisplayName("name 이 비어 있으면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenNameIsBlank() {
            // arrange
            val body = mapOf("name" to "", "description" to "설명")

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                HttpEntity(body, adminHeaders()),
                brandType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("name 이 50자를 넘으면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenNameIsTooLong() {
            // arrange
            val body = mapOf("name" to "가".repeat(51))

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                HttpEntity(body, adminHeaders()),
                brandType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("name 필드 자체가 없으면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenNameFieldIsMissing() {
            // arrange
            val body = mapOf("description" to "설명")

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                HttpEntity(body, adminHeaders()),
                brandType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("인증 헤더가 없으면, 401 Unauthorized 를 반환한다.")
        @Test
        fun returnsUnauthorized_whenHeadersAreMissing() {
            // arrange
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val body = mapOf("name" to "루퍼스")

            // act
            val response = testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, HttpEntity(body, headers), brandType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @DisplayName("PUT /api-admin/v1/brands/{brandId}")
    @Nested
    inner class ChangeBrand {
        @DisplayName("브랜드를 수정하면, 교체된 정보를 반환한다.")
        @Test
        fun changesBrand() {
            // arrange
            val brand = saveBrand()
            val body = mapOf("name" to "몬드리안", "description" to "선과 면")

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${brand.id}",
                HttpMethod.PUT,
                HttpEntity(body, adminHeaders()),
                brandType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.name).isEqualTo("몬드리안") },
                { assertThat(response.body?.data?.description).isEqualTo("선과 면") },
            )
        }

        /**
         * PUT 은 전체 교체다. description 을 생략하면 기존 값이 유지되는 것이 아니라 빈 문자열로 덮인다.
         * "생략하면 유지" 는 PATCH 의 의미이며 이 API 의 계약이 아니다.
         */
        @DisplayName("description 을 생략하면, 기존 설명이 빈 문자열로 덮인다.")
        @Test
        fun overwritesDescriptionWithEmpty_whenDescriptionIsOmitted() {
            // arrange
            val brand = saveBrand(description = "일상을 조금 낫게")
            val body = mapOf("name" to "루퍼스")

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${brand.id}",
                HttpMethod.PUT,
                HttpEntity(body, adminHeaders()),
                brandType,
            )

            // assert
            assertThat(response.body?.data?.description).isEmpty()
        }

        @DisplayName("존재하지 않는 브랜드를 수정하면, 404 Not Found 를 반환한다.")
        @Test
        fun returnsNotFound_whenBrandDoesNotExist() {
            // arrange
            val body = mapOf("name" to "몬드리안")

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/99999",
                HttpMethod.PUT,
                HttpEntity(body, adminHeaders()),
                brandType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        /**
         * 어드민은 삭제된 브랜드를 조회할 수 있으므로 그것은 "없는" 것이 아니다.
         * 요청은 멀쩡하고 리소스도 존재하지만 현재 상태와 충돌하므로 404 가 아니라 409 다.
         */
        @DisplayName("삭제된 브랜드를 수정하면, 409 Conflict 를 반환한다.")
        @Test
        fun returnsConflict_whenBrandIsDeleted() {
            // arrange
            val brand = saveBrand()
            brand.delete()
            brandRepository.save(brand)
            val body = mapOf("name" to "몬드리안")

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${brand.id}",
                HttpMethod.PUT,
                HttpEntity(body, adminHeaders()),
                brandType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }
    }

    @DisplayName("DELETE /api-admin/v1/brands/{brandId}")
    @Nested
    inner class DeleteBrand {
        @DisplayName("브랜드를 삭제하면, 200 을 반환하고 이후 조회에서 deleted 가 true 다.")
        @Test
        fun deletesBrand() {
            // arrange
            val brand = saveBrand()

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${brand.id}",
                HttpMethod.DELETE,
                HttpEntity<Any>(adminHeaders()),
                brandType,
            )

            // assert
            val found = testRestTemplate.exchange(
                "$ENDPOINT/${brand.id}",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                brandType,
            )
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(found.body?.data?.deleted).isTrue() },
            )
        }

        @DisplayName("브랜드를 삭제하면, 그 브랜드의 상품도 공개 조회에서 사라진다.")
        @Test
        fun cascadesToProducts() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)

            // act
            testRestTemplate.exchange(
                "$ENDPOINT/${brand.id}",
                HttpMethod.DELETE,
                HttpEntity<Any>(adminHeaders()),
                brandType,
            )

            // assert
            assertThat(productService.getProduct(product.id)).isNull()
        }

        @DisplayName("존재하지 않는 브랜드를 삭제하면, 404 Not Found 를 반환한다.")
        @Test
        fun returnsNotFound_whenBrandDoesNotExist() {
            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/99999",
                HttpMethod.DELETE,
                HttpEntity<Any>(adminHeaders()),
                brandType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("이미 삭제된 브랜드를 다시 삭제해도, 200 을 반환한다.")
        @Test
        fun isIdempotent() {
            // arrange
            val brand = saveBrand()
            testRestTemplate.exchange(
                "$ENDPOINT/${brand.id}",
                HttpMethod.DELETE,
                HttpEntity<Any>(adminHeaders()),
                brandType,
            )

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${brand.id}",
                HttpMethod.DELETE,
                HttpEntity<Any>(adminHeaders()),
                brandType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        }
    }
}
