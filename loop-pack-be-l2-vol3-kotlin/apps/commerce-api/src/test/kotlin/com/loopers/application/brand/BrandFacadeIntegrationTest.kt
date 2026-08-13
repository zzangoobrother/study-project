package com.loopers.application.brand

import com.loopers.domain.brand.BrandDescription
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class BrandFacadeIntegrationTest @Autowired constructor(
    private val brandFacade: BrandFacade,
    private val brandRepository: BrandRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private fun saveBrand(name: String = "루퍼스", description: BrandDescription = BrandDescription.EMPTY): BrandModel =
        brandRepository.save(BrandModel.create(BrandName(name), description))

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("브랜드를 조회할 때, ")
    @Nested
    inner class GetBrand {
        @DisplayName("존재하는 브랜드면, BrandInfo 가 반환된다.")
        @Test
        fun returnsBrandInfo_whenBrandExists() {
            // arrange
            val brand = saveBrand(name = "루퍼스", description = BrandDescription("스트릿 브랜드"))

            // act
            val info = brandFacade.getBrand(brand.id)

            // assert
            assertAll(
                { assertThat(info.id).isEqualTo(brand.id) },
                { assertThat(info.name).isEqualTo(BrandName("루퍼스")) },
                { assertThat(info.description).isEqualTo(BrandDescription("스트릿 브랜드")) },
            )
        }

        @DisplayName("존재하지 않는 브랜드면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFoundException_whenBrandDoesNotExist() {
            // act
            val result = assertThrows<CoreException> { brandFacade.getBrand(99999L) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("소프트 삭제된 브랜드면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFoundException_whenBrandIsSoftDeleted() {
            // arrange
            val brand = saveBrand()
            brand.delete()
            brandRepository.save(brand)

            // act
            val result = assertThrows<CoreException> { brandFacade.getBrand(brand.id) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
