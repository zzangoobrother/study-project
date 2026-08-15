package com.loopers.domain.brand

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
class BrandServiceIntegrationTest @Autowired constructor(
    private val brandService: BrandService,
    private val brandRepository: BrandRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val jdbcTemplate: JdbcTemplate,
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

    @DisplayName("삭제 포함으로 브랜드를 단건 조회할 때, ")
    @Nested
    inner class GetBrandIncludingDeleted {
        @DisplayName("살아 있는 브랜드가 반환된다.")
        @Test
        fun returnsAliveBrand() {
            // arrange
            val saved = saveBrand()

            // act
            val found = brandService.getBrandIncludingDeleted(saved.id)

            // assert
            assertThat(found?.id).isEqualTo(saved.id)
        }

        /**
         * 공개 조회(getBrand)와 정반대의 계약이다.
         * 어드민은 삭제된 리소스도 볼 수 있어야 하며, 이것이 어드민 전용 조회 경로가 필요한 이유다.
         */
        @DisplayName("소프트 삭제된 브랜드도 반환된다.")
        @Test
        fun returnsSoftDeletedBrand() {
            // arrange
            val saved = saveBrand()
            saved.delete()
            brandRepository.save(saved)

            // act
            val found = brandService.getBrandIncludingDeleted(saved.id)

            // assert
            assertAll(
                { assertThat(found?.id).isEqualTo(saved.id) },
                { assertThat(found?.deletedAt).isNotNull() },
            )
        }

        @DisplayName("존재하지 않는 ID 면, null 이 반환된다.")
        @Test
        fun returnsNull_whenBrandDoesNotExist() {
            // act
            val found = brandService.getBrandIncludingDeleted(99999L)

            // assert
            assertThat(found).isNull()
        }
    }

    @DisplayName("삭제 포함으로 브랜드를 여러 건 조회할 때, ")
    @Nested
    inner class GetBrandsIncludingDeleted {
        @DisplayName("삭제된 브랜드도 결과에 포함된다.")
        @Test
        fun includesSoftDeletedBrands() {
            // arrange
            val alive = saveBrand(name = "루퍼스")
            val deleted = saveBrand(name = "몬드리안")
            deleted.delete()
            brandRepository.save(deleted)

            // act
            val found = brandService.getBrandsIncludingDeleted(listOf(alive.id, deleted.id))

            // assert
            assertThat(found.map { it.id }).containsExactlyInAnyOrder(alive.id, deleted.id)
        }

        @DisplayName("ID 목록이 비어 있으면, 빈 목록이 반환된다.")
        @Test
        fun returnsEmptyList_whenIdsAreEmpty() {
            // act
            val found = brandService.getBrandsIncludingDeleted(emptyList())

            // assert
            assertThat(found).isEmpty()
        }
    }

    @DisplayName("삭제 포함으로 브랜드 목록을 페이징 조회할 때, ")
    @Nested
    inner class GetBrandPageIncludingDeleted {
        @DisplayName("삭제된 브랜드도 content 와 totalElements 양쪽에 포함된다.")
        @Test
        fun includesSoftDeletedBrands() {
            // arrange
            saveBrand(name = "루퍼스")
            val deleted = saveBrand(name = "몬드리안")
            deleted.delete()
            brandRepository.save(deleted)

            // act
            val page = brandService.getBrandPageIncludingDeleted(PageQuery(page = 0, size = 20))

            // assert
            assertAll(
                { assertThat(page.content).hasSize(2) },
                { assertThat(page.totalElements).isEqualTo(2L) },
            )
        }

        @DisplayName("최신순으로 정렬된다.")
        @Test
        fun sortsByCreatedAtDesc() {
            // arrange
            val first = saveBrand(name = "루퍼스")
            val second = saveBrand(name = "몬드리안")
            val third = saveBrand(name = "하바나")

            // act
            val page = brandService.getBrandPageIncludingDeleted(PageQuery(page = 0, size = 20))

            // assert
            assertThat(page.content.map { it.id }).containsExactly(third.id, second.id, first.id)
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
            val first = saveBrand(name = "루퍼스")
            val second = saveBrand(name = "몬드리안")
            val third = saveBrand(name = "하바나")
            jdbcTemplate.update("UPDATE brands SET created_at = ?", java.sql.Timestamp.valueOf("2026-01-01 00:00:00"))

            // act
            val page = brandService.getBrandPageIncludingDeleted(PageQuery(page = 0, size = 20))

            // assert
            assertThat(page.content.map { it.id }).containsExactly(third.id, second.id, first.id)
        }

        @DisplayName("마지막 페이지를 넘어선 요청이면, content 는 비고 totalElements 는 유지된다.")
        @Test
        fun returnsEmptyContent_whenPageIsBeyondLast() {
            // arrange
            saveBrand(name = "루퍼스")

            // act
            val page = brandService.getBrandPageIncludingDeleted(PageQuery(page = 100, size = 20))

            // assert
            assertAll(
                { assertThat(page.content).isEmpty() },
                { assertThat(page.totalElements).isEqualTo(1L) },
            )
        }
    }

    @DisplayName("브랜드를 등록할 때, ")
    @Nested
    inner class Register {
        @DisplayName("브랜드가 저장되고 ID 가 부여된다.")
        @Test
        fun savesBrand() {
            // arrange
            val command = BrandCommand.Register(BrandName("루퍼스"), BrandDescription("일상을 조금 낫게"))

            // act
            val registered = brandService.register(command)

            // assert
            assertAll(
                { assertThat(registered.id).isPositive() },
                { assertThat(registered.name).isEqualTo(BrandName("루퍼스")) },
                { assertThat(registered.deletedAt).isNull() },
            )
        }

        @DisplayName("등록한 브랜드를 다시 조회할 수 있다.")
        @Test
        fun registeredBrandIsRetrievable() {
            // arrange
            val command = BrandCommand.Register(BrandName("루퍼스"), BrandDescription("일상을 조금 낫게"))

            // act
            val registered = brandService.register(command)

            // assert
            assertThat(brandService.getBrand(registered.id)?.name).isEqualTo(BrandName("루퍼스"))
        }
    }

    @DisplayName("브랜드를 수정할 때, ")
    @Nested
    inner class Change {
        @DisplayName("이름과 설명이 교체된다.")
        @Test
        fun changesNameAndDescription() {
            // arrange
            val saved = saveBrand()

            // act
            brandService.change(BrandCommand.Change(saved.id, BrandName("몬드리안"), BrandDescription("선과 면")))

            // assert
            val found = brandService.getBrand(saved.id)
            assertAll(
                { assertThat(found?.name).isEqualTo(BrandName("몬드리안")) },
                { assertThat(found?.description).isEqualTo(BrandDescription("선과 면")) },
            )
        }

        @DisplayName("존재하지 않는 브랜드면, NOT_FOUND 를 던진다.")
        @Test
        fun throwsNotFound_whenBrandDoesNotExist() {
            // act & assert
            assertThatThrownBy {
                brandService.change(BrandCommand.Change(99999L, BrandName("몬드리안"), BrandDescription.EMPTY))
            }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.NOT_FOUND)
        }

        /**
         * 삭제된 브랜드는 어드민 조회에서 보이므로 "없는" 것이 아니다.
         * 요청은 멀쩡하고 리소스도 존재하지만 리소스의 현재 상태와 충돌하므로 409 다.
         */
        @DisplayName("소프트 삭제된 브랜드면, CONFLICT 를 던진다.")
        @Test
        fun throwsConflict_whenBrandIsSoftDeleted() {
            // arrange
            val saved = saveBrand()
            saved.delete()
            brandRepository.save(saved)

            // act & assert
            assertThatThrownBy {
                brandService.change(BrandCommand.Change(saved.id, BrandName("몬드리안"), BrandDescription.EMPTY))
            }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.CONFLICT)
        }
    }

    @DisplayName("브랜드를 삭제할 때, ")
    @Nested
    inner class Delete {
        @DisplayName("deletedAt 이 찍히고 공개 조회에서 사라진다.")
        @Test
        fun softDeletesBrand() {
            // arrange
            val saved = saveBrand()

            // act
            brandService.delete(saved.id)

            // assert
            assertAll(
                { assertThat(brandService.getBrand(saved.id)).isNull() },
                { assertThat(brandService.getBrandIncludingDeleted(saved.id)?.deletedAt).isNotNull() },
            )
        }

        @DisplayName("존재하지 않는 브랜드면, NOT_FOUND 를 던진다.")
        @Test
        fun throwsNotFound_whenBrandDoesNotExist() {
            // act & assert
            assertThatThrownBy { brandService.delete(99999L) }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.NOT_FOUND)
        }

        /**
         * BaseEntity.delete() 가 deletedAt ?: run { ... } 로 멱등하다.
         * 두 번째 삭제가 예외를 던지지 않고 deletedAt 을 갱신하지도 않아야 한다.
         */
        @DisplayName("이미 삭제된 브랜드를 다시 삭제해도, 예외 없이 deletedAt 이 유지된다.")
        @Test
        fun isIdempotent() {
            // arrange
            val saved = saveBrand()
            brandService.delete(saved.id)
            val firstDeletedAt = brandService.getBrandIncludingDeleted(saved.id)?.deletedAt

            // act
            brandService.delete(saved.id)

            // assert
            assertThat(brandService.getBrandIncludingDeleted(saved.id)?.deletedAt).isEqualTo(firstDeletedAt)
        }
    }
}
