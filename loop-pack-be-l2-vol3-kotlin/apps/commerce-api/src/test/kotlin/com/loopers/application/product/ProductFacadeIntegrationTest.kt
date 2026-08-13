package com.loopers.application.product

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.brand.BrandService
import com.loopers.domain.product.LikeCount
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductCriteria
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSortType
import com.loopers.domain.support.PageQuery
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean

@SpringBootTest
class ProductFacadeIntegrationTest @Autowired constructor(
    private val productFacade: ProductFacade,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockitoSpyBean
    private lateinit var brandService: BrandService

    private fun saveBrand(name: String): BrandModel = brandRepository.save(BrandModel.create(BrandName(name)))

    private fun saveProducts(vararg products: ProductModel): List<ProductModel> =
        productRepository.saveAll(products.toList())

    private fun product(brandId: Long, name: String = "상품", price: Long = 10_000, likeCount: Long = 0) =
        ProductModel.create(
            brandId = brandId,
            name = ProductName(name),
            price = Price(price),
            likeCount = LikeCount(likeCount),
        )

    private fun search(brandId: Long? = null, page: Int = 0, size: Int = 20) = ProductCriteria.Search(
        brandId = brandId,
        sort = ProductSortType.LATEST,
        pageQuery = PageQuery(page, size),
    )

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("상품 목록을 조회할 때, ")
    @Nested
    inner class GetProducts {
        @DisplayName("각 상품에 브랜드 정보가 결합되어 반환된다.")
        @Test
        fun combinesBrandInfoIntoEachProduct() {
            // arrange
            val brand = saveBrand("루퍼스")
            saveProducts(product(brandId = brand.id, name = "베이직 티셔츠"))

            // act
            val result = productFacade.getProducts(search())

            // assert
            val info = result.content.first()
            assertAll(
                { assertThat(info.name).isEqualTo(ProductName("베이직 티셔츠")) },
                { assertThat(info.brand?.id).isEqualTo(brand.id) },
                { assertThat(info.brand?.name).isEqualTo(BrandName("루퍼스")) },
            )
        }

        @DisplayName("여러 상품이 같은 브랜드를 가리켜도, 브랜드 조회는 1회만 수행된다.")
        @Test
        fun queriesBrandsOnlyOnce_regardlessOfProductCount() {
            // arrange
            val brand = saveBrand("루퍼스")
            saveProducts(*Array(10) { product(brandId = brand.id, name = "상품${it + 1}") })

            // act
            productFacade.getProducts(search())

            // assert
            verify(brandService, times(1)).getBrands(listOf(brand.id))
        }

        @DisplayName("브랜드가 소프트 삭제된 상품도 목록에서 빠지지 않고, brand 만 null 이 된다.")
        @Test
        fun keepsProductWithNullBrand_whenBrandIsSoftDeleted() {
            // arrange
            val alive = saveBrand("루퍼스")
            val deleted = saveBrand("몬드리안")
            saveProducts(
                product(brandId = alive.id, name = "살아있는 브랜드 상품"),
                product(brandId = deleted.id, name = "삭제된 브랜드 상품"),
            )
            deleted.delete()
            brandRepository.save(deleted)

            // act
            val result = productFacade.getProducts(search())

            // assert
            val orphan = result.content.first { it.name == ProductName("삭제된 브랜드 상품") }
            assertAll(
                { assertThat(result.content).hasSize(2) },
                { assertThat(result.totalElements).isEqualTo(2L) },
                { assertThat(orphan.brand).isNull() },
            )
        }

        @DisplayName("상품이 없으면, 브랜드 조회 없이 빈 목록이 반환된다.")
        @Test
        fun returnsEmptyResult_whenNoProductMatches() {
            // act
            val result = productFacade.getProducts(search(brandId = 99999L))

            // assert
            assertAll(
                { assertThat(result.content).isEmpty() },
                { assertThat(result.totalElements).isZero() },
                { verify(brandService, times(1)).getBrands(emptyList()) },
            )
        }

        @DisplayName("페이징 메타 정보가 그대로 보존된다.")
        @Test
        fun preservesPagingMetadata() {
            // arrange
            val brand = saveBrand("루퍼스")
            saveProducts(*Array(25) { product(brandId = brand.id, name = "상품${it + 1}") })

            // act
            val result = productFacade.getProducts(search(page = 1, size = 10))

            // assert
            assertAll(
                { assertThat(result.page).isEqualTo(1) },
                { assertThat(result.size).isEqualTo(10) },
                { assertThat(result.totalElements).isEqualTo(25L) },
                { assertThat(result.totalPages).isEqualTo(3) },
            )
        }
    }

    @DisplayName("상품을 단건 조회할 때, ")
    @Nested
    inner class GetProduct {
        @DisplayName("브랜드 정보가 결합되어 반환된다.")
        @Test
        fun combinesBrandInfo() {
            // arrange
            val brand = saveBrand("루퍼스")
            val saved = saveProducts(product(brandId = brand.id, name = "베이직 티셔츠", likeCount = 42)).first()

            // act
            val info = productFacade.getProduct(saved.id)

            // assert
            assertAll(
                { assertThat(info.id).isEqualTo(saved.id) },
                { assertThat(info.name).isEqualTo(ProductName("베이직 티셔츠")) },
                { assertThat(info.likeCount).isEqualTo(LikeCount(42)) },
                { assertThat(info.brand?.name).isEqualTo(BrandName("루퍼스")) },
            )
        }

        @DisplayName("브랜드가 소프트 삭제되었으면, brand 가 null 인 채로 반환된다.")
        @Test
        fun returnsNullBrand_whenBrandIsSoftDeleted() {
            // arrange
            val brand = saveBrand("몬드리안")
            val saved = saveProducts(product(brandId = brand.id)).first()
            brand.delete()
            brandRepository.save(brand)

            // act
            val info = productFacade.getProduct(saved.id)

            // assert
            assertThat(info.brand).isNull()
        }

        @DisplayName("상품이 없으면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFoundException_whenProductDoesNotExist() {
            // act
            val result = assertThrows<CoreException> { productFacade.getProduct(99999L) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
