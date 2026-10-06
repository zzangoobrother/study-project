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
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductSortType
import com.loopers.domain.support.PageQuery
import com.loopers.support.error.CoreException
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
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
class ProductCacheIntegrationTest @Autowired constructor(
    private val productFacade: ProductFacade,
    private val productCache: ProductCache,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @MockitoSpyBean
    private lateinit var productService: ProductService

    @MockitoSpyBean
    private lateinit var brandService: BrandService

    private fun saveBrand(name: String = "루퍼스"): BrandModel = brandRepository.save(BrandModel.create(BrandName(name)))

    private fun saveProduct(brandId: Long, name: String = "상품", likeCount: Long = 0): ProductModel =
        productRepository.save(
            ProductModel.create(
                brandId = brandId,
                name = ProductName(name),
                price = Price(10_000),
                likeCount = LikeCount(likeCount),
            ),
        )

    private fun search(brandId: Long? = null) =
        ProductCriteria.Search(brandId = brandId, sort = ProductSortType.LATEST, pageQuery = PageQuery(0, 20))

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("같은 상품을 다시 조회할 때, ")
    @Nested
    inner class ReadAgain {
        @DisplayName("상세는 두 번째부터 DB 를 부르지 않는다.")
        @Test
        fun readsDetailFromCache() {
            // arrange
            val saved = saveProduct(brandId = saveBrand().id, name = "베이직 티셔츠")

            // act
            val first = productFacade.getProduct(saved.id)
            val second = productFacade.getProduct(saved.id)

            // assert
            assertAll(
                { assertThat(second).isEqualTo(first) },
                { verify(productService, times(1)).getProduct(saved.id) },
            )
        }

        @DisplayName("목록은 두 번째부터 DB 를 부르지 않는다.")
        @Test
        fun readsListFromCache() {
            // arrange
            val brand = saveBrand()
            saveProduct(brandId = brand.id, name = "상품1")
            saveProduct(brandId = brand.id, name = "상품2")

            // act
            val first = productFacade.getProducts(search())
            val second = productFacade.getProducts(search())

            // assert
            assertAll(
                { assertThat(second).isEqualTo(first) },
                { verify(productService, times(1)).getProducts(search()) },
            )
        }

        @DisplayName("브랜드도 두 번째부터 DB 를 부르지 않는다.")
        @Test
        fun readsBrandFromCache() {
            // arrange
            val brand = saveBrand()
            val saved = saveProduct(brandId = brand.id)

            // act
            productFacade.getProduct(saved.id)
            productFacade.getProduct(saved.id)

            // assert
            verify(brandService, times(1)).getBrands(listOf(brand.id))
        }

        @DisplayName("없는 상품은 캐시하지 않아 매번 DB 가 404 를 판정한다.")
        @Test
        fun doesNotCacheMissingProduct() {
            // act
            repeat(2) { assertThrows<CoreException> { productFacade.getProduct(99999L) } }

            // assert
            assertAll(
                { verify(productService, times(2)).getProduct(99999L) },
                { assertThat(productCache.getProduct(99999L)).isNull() },
            )
        }
    }
}
