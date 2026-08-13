package com.loopers.interfaces.api

import com.loopers.domain.brand.BrandDescription
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.interfaces.api.brand.BrandV1Dto
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
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandRepository: BrandRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT_BRAND = "/api/v1/brands"
    }

    private val responseType = object : ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>>() {}

    private fun saveBrand(name: String = "루퍼스", description: String = "일상을 조금 낫게"): BrandModel =
        brandRepository.save(BrandModel.create(BrandName(name), BrandDescription(description)))

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("GET /api/v1/brands/{brandId}")
    @Nested
    inner class GetBrand {
        @DisplayName("존재하는 브랜드를 조회하면, 브랜드 정보를 반환한다.")
        @Test
        fun returnsBrand_whenBrandExists() {
            // arrange
            val brand = saveBrand()

            // act
            val response = testRestTemplate.exchange("$ENDPOINT_BRAND/${brand.id}", HttpMethod.GET, null, responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data?.id).isEqualTo(brand.id) },
                { assertThat(response.body?.data?.name).isEqualTo("루퍼스") },
                { assertThat(response.body?.data?.description).isEqualTo("일상을 조금 낫게") },
            )
        }

        @DisplayName("설명이 없는 브랜드를 조회하면, description 이 빈 문자열로 반환된다.")
        @Test
        fun returnsEmptyDescription_whenBrandHasNoDescription() {
            // arrange
            val brand = brandRepository.save(BrandModel.create(BrandName("하바나")))

            // act
            val response = testRestTemplate.exchange("$ENDPOINT_BRAND/${brand.id}", HttpMethod.GET, null, responseType)

            // assert
            assertThat(response.body?.data?.description).isEmpty()
        }

        @DisplayName("존재하지 않는 브랜드를 조회하면, 404 Not Found 를 반환한다.")
        @Test
        fun returnsNotFound_whenBrandDoesNotExist() {
            // act
            val response = testRestTemplate.exchange("$ENDPOINT_BRAND/99999", HttpMethod.GET, null, responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("소프트 삭제된 브랜드를 조회하면, 404 Not Found 를 반환한다.")
        @Test
        fun returnsNotFound_whenBrandIsSoftDeleted() {
            // arrange
            val brand = saveBrand()
            brand.delete()
            brandRepository.save(brand)

            // act
            val response = testRestTemplate.exchange("$ENDPOINT_BRAND/${brand.id}", HttpMethod.GET, null, responseType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("브랜드 ID 가 숫자가 아니면, 400 Bad Request 를 반환한다.")
        @Test
        fun returnsBadRequest_whenBrandIdIsNotNumeric() {
            // act
            val response = testRestTemplate.exchange("$ENDPOINT_BRAND/abc", HttpMethod.GET, null, responseType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }
}
