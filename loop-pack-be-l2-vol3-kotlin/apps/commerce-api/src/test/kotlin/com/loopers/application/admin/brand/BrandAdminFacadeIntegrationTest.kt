package com.loopers.application.admin.brand

import com.loopers.domain.brand.BrandCommand
import com.loopers.domain.brand.BrandDescription
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductService
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

@SpringBootTest
class BrandAdminFacadeIntegrationTest @Autowired constructor(
    private val brandAdminFacade: BrandAdminFacade,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val productService: ProductService,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private fun saveBrand(name: String = "루퍼스", description: String = "일상을 조금 낫게"): BrandModel =
        brandRepository.save(BrandModel.create(BrandName(name), BrandDescription(description)))

    private fun saveProduct(brandId: Long, name: String = "운동화"): ProductModel =
        productRepository.save(ProductModel.create(brandId = brandId, name = ProductName(name), price = Price(39000)))

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("브랜드를 단건 조회할 때, ")
    @Nested
    inner class GetBrand {
        @DisplayName("살아 있는 브랜드의 정보가 반환되고 deleted 는 false 다.")
        @Test
        fun returnsAliveBrand() {
            // arrange
            val saved = saveBrand()

            // act
            val info = brandAdminFacade.getBrand(saved.id)

            // assert
            assertAll(
                { assertThat(info.id).isEqualTo(saved.id) },
                { assertThat(info.name).isEqualTo(BrandName("루퍼스")) },
                { assertThat(info.deleted).isFalse() },
                { assertThat(info.deletedAt).isNull() },
                { assertThat(info.createdAt).isNotNull() },
            )
        }

        @DisplayName("삭제된 브랜드도 반환되고 deleted 는 true 다.")
        @Test
        fun returnsDeletedBrand() {
            // arrange
            val saved = saveBrand()
            saved.delete()
            brandRepository.save(saved)

            // act
            val info = brandAdminFacade.getBrand(saved.id)

            // assert
            assertAll(
                { assertThat(info.deleted).isTrue() },
                { assertThat(info.deletedAt).isNotNull() },
            )
        }

        @DisplayName("존재하지 않는 브랜드면, NOT_FOUND 를 던진다.")
        @Test
        fun throwsNotFound_whenBrandDoesNotExist() {
            // act & assert
            assertThatThrownBy { brandAdminFacade.getBrand(99999L) }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("브랜드 목록을 조회할 때, ")
    @Nested
    inner class GetBrands {
        @DisplayName("삭제된 브랜드도 포함되어 최신순으로 반환된다.")
        @Test
        fun includesDeletedBrandsInLatestOrder() {
            // arrange
            val first = saveBrand(name = "루퍼스")
            val second = saveBrand(name = "몬드리안")
            second.delete()
            brandRepository.save(second)

            // act
            val page = brandAdminFacade.getBrands(PageQuery(0, 20))

            // assert
            assertAll(
                { assertThat(page.content.map { it.id }).containsExactly(second.id, first.id) },
                { assertThat(page.content.map { it.deleted }).containsExactly(true, false) },
                { assertThat(page.totalElements).isEqualTo(2L) },
            )
        }
    }

    @DisplayName("브랜드를 등록하고 수정할 때, ")
    @Nested
    inner class RegisterAndChange {
        @DisplayName("등록하면 정보가 반환되고 타임스탬프가 채워진다.")
        @Test
        fun returnsRegisteredBrand() {
            // act
            val info = brandAdminFacade.register(
                BrandCommand.Register(BrandName("루퍼스"), BrandDescription("일상을 조금 낫게")),
            )

            // assert
            assertAll(
                { assertThat(info.id).isPositive() },
                { assertThat(info.deleted).isFalse() },
                { assertThat(info.createdAt).isNotNull() },
                { assertThat(info.updatedAt).isNotNull() },
            )
        }

        @DisplayName("수정하면 교체된 정보가 반환된다.")
        @Test
        fun returnsChangedBrand() {
            // arrange
            val saved = saveBrand()

            // act
            val info = brandAdminFacade.change(
                BrandCommand.Change(saved.id, BrandName("몬드리안"), BrandDescription("선과 면")),
            )

            // assert
            assertAll(
                { assertThat(info.name).isEqualTo(BrandName("몬드리안")) },
                { assertThat(info.description).isEqualTo(BrandDescription("선과 면")) },
            )
        }
    }

    @DisplayName("브랜드를 삭제할 때, ")
    @Nested
    inner class Delete {
        @DisplayName("브랜드와 그 브랜드의 상품이 함께 소프트 삭제된다.")
        @Test
        fun cascadesToProducts() {
            // arrange
            val brand = saveBrand()
            val first = saveProduct(brand.id, name = "운동화")
            val second = saveProduct(brand.id, name = "러닝화")

            // act
            brandAdminFacade.delete(brand.id)

            // assert
            assertAll(
                { assertThat(brandAdminFacade.getBrand(brand.id).deleted).isTrue() },
                { assertThat(productService.getProduct(first.id)).isNull() },
                { assertThat(productService.getProduct(second.id)).isNull() },
            )
        }

        /**
         * 이 테스트가 연쇄 삭제에서 가장 중요하다.
         * 브랜드 필터를 빠뜨리면 전체 상품이 삭제되는데, 대상 브랜드의 상품만 확인하는 테스트는 그것을 통과시킨다.
         */
        @DisplayName("다른 브랜드의 상품은 삭제되지 않는다.")
        @Test
        fun doesNotTouchOtherBrandsProducts() {
            // arrange
            val target = saveBrand(name = "루퍼스")
            val other = saveBrand(name = "몬드리안")
            val targetProduct = saveProduct(target.id, name = "운동화")
            val otherProduct = saveProduct(other.id, name = "러닝화")

            // act
            brandAdminFacade.delete(target.id)

            // assert
            assertAll(
                { assertThat(productService.getProduct(targetProduct.id)).isNull() },
                { assertThat(productService.getProduct(otherProduct.id)).isNotNull() },
                { assertThat(brandAdminFacade.getBrand(other.id).deleted).isFalse() },
            )
        }

        @DisplayName("상품이 없는 브랜드를 삭제해도, 예외 없이 브랜드만 삭제된다.")
        @Test
        fun deletesBrandWithoutProducts() {
            // arrange
            val brand = saveBrand()

            // act
            brandAdminFacade.delete(brand.id)

            // assert
            assertThat(brandAdminFacade.getBrand(brand.id).deleted).isTrue()
        }

        @DisplayName("존재하지 않는 브랜드면, NOT_FOUND 를 던진다.")
        @Test
        fun throwsNotFound_whenBrandDoesNotExist() {
            // act & assert
            assertThatThrownBy { brandAdminFacade.delete(99999L) }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.NOT_FOUND)
        }

        /**
         * 두 번째 호출이 살아 있는 상품만 조회하므로 아무 일도 일어나지 않는다.
         * 브랜드는 삭제됐는데 상품 삭제가 실패했던 상태가 있다면 재호출이 그것을 복구하는 부수 효과도 있다.
         */
        @DisplayName("이미 삭제된 브랜드를 다시 삭제해도, 예외가 발생하지 않는다.")
        @Test
        fun isIdempotent() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)
            brandAdminFacade.delete(brand.id)
            val deletedAt = brandAdminFacade.getBrand(brand.id).deletedAt

            // act
            brandAdminFacade.delete(brand.id)

            // assert
            assertAll(
                { assertThat(brandAdminFacade.getBrand(brand.id).deletedAt).isEqualTo(deletedAt) },
                { assertThat(productService.getProductIncludingDeleted(product.id)?.deletedAt).isNotNull() },
            )
        }
    }
}
