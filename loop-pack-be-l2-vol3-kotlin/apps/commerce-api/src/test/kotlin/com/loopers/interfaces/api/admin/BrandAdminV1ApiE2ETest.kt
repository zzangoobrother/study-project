package com.loopers.interfaces.api.admin

import com.loopers.domain.brand.BrandDescription
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
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
}
