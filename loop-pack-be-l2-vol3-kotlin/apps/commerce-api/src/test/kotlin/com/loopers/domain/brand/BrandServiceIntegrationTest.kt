package com.loopers.domain.brand

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
class BrandServiceIntegrationTest @Autowired constructor(
    private val brandService: BrandService,
    private val brandRepository: BrandRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private fun saveBrand(name: String = "루퍼스", description: String = "일상을 조금 낫게"): BrandModel =
        brandRepository.save(BrandModel.create(BrandName(name), BrandDescription(description)))

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("브랜드를 단건 조회할 때, ")
    @Nested
    inner class GetBrand {
        @DisplayName("해당 ID 의 브랜드가 있으면, 브랜드가 반환된다.")
        @Test
        fun returnsBrand_whenBrandExists() {
            // arrange
            val saved = saveBrand()

            // act
            val found = brandService.getBrand(saved.id)

            // assert
            assertAll(
                { assertThat(found).isNotNull() },
                { assertThat(found?.name).isEqualTo(BrandName("루퍼스")) },
                { assertThat(found?.description).isEqualTo(BrandDescription("일상을 조금 낫게")) },
            )
        }

        @DisplayName("해당 ID 의 브랜드가 없으면, null 이 반환된다.")
        @Test
        fun returnsNull_whenBrandDoesNotExist() {
            // act
            val found = brandService.getBrand(99999L)

            // assert
            assertThat(found).isNull()
        }

        @DisplayName("소프트 삭제된 브랜드는, null 이 반환된다.")
        @Test
        fun returnsNull_whenBrandIsSoftDeleted() {
            // arrange
            val saved = saveBrand()
            saved.delete()
            brandRepository.save(saved)

            // act
            val found = brandService.getBrand(saved.id)

            // assert
            assertThat(found).isNull()
        }
    }

    @DisplayName("브랜드를 여러 건 조회할 때, ")
    @Nested
    inner class GetBrands {
        @DisplayName("요청한 ID 에 해당하는 브랜드들이 반환된다.")
        @Test
        fun returnsBrands_forGivenIds() {
            // arrange
            val first = saveBrand(name = "루퍼스")
            val second = saveBrand(name = "몬드리안")
            saveBrand(name = "하바나")

            // act
            val found = brandService.getBrands(listOf(first.id, second.id))

            // assert
            assertThat(found.map { it.id }).containsExactlyInAnyOrder(first.id, second.id)
        }

        @DisplayName("소프트 삭제된 브랜드는, 결과에서 제외된다.")
        @Test
        fun excludesSoftDeletedBrands() {
            // arrange
            val alive = saveBrand(name = "루퍼스")
            val deleted = saveBrand(name = "몬드리안")
            deleted.delete()
            brandRepository.save(deleted)

            // act
            val found = brandService.getBrands(listOf(alive.id, deleted.id))

            // assert
            assertThat(found.map { it.id }).containsExactly(alive.id)
        }

        @DisplayName("ID 목록이 비어 있으면, 빈 목록이 반환된다.")
        @Test
        fun returnsEmptyList_whenIdsAreEmpty() {
            // act
            val found = brandService.getBrands(emptyList())

            // assert
            assertThat(found).isEmpty()
        }
    }
}
