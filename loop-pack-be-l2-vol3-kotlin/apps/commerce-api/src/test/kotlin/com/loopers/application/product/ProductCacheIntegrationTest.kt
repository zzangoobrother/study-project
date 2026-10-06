package com.loopers.application.product

import com.loopers.application.admin.brand.BrandAdminFacade
import com.loopers.application.admin.product.ProductAdminFacade
import com.loopers.application.like.LikeFacade
import com.loopers.domain.brand.BrandCommand
import com.loopers.domain.brand.BrandDescription
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.brand.BrandService
import com.loopers.domain.product.LikeCount
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductCommand
import com.loopers.domain.product.ProductCriteria
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductSortType
import com.loopers.domain.product.Stock
import com.loopers.domain.support.PageQuery
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserName
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
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
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
class ProductCacheIntegrationTest @Autowired constructor(
    private val productFacade: ProductFacade,
    private val productCache: ProductCache,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
    private val likeFacade: LikeFacade,
    private val productAdminFacade: ProductAdminFacade,
    private val brandAdminFacade: BrandAdminFacade,
    private val userService: UserService,
    private val transactionTemplate: TransactionTemplate,
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

    private fun signUp(loginId: String = "loopers01"): LoginId {
        userService.signUp(
            UserCommand.SignUp(
                loginId = LoginId(loginId),
                password = RawPassword("Loopers1!"),
                name = UserName("홍길동"),
                birthDate = BirthDate.from("1990-01-01"),
                email = Email("$loginId@loopers.com"),
            ),
        )
        return LoginId(loginId)
    }

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

    @DisplayName("쓰기 뒤에 상세를 다시 조회하면, ")
    @Nested
    inner class DetailAfterWrite {
        @DisplayName("좋아요 뒤에는 새 카운트가 보인다.")
        @Test
        fun showsNewCount_afterLike() {
            // arrange
            val saved = saveProduct(brandId = saveBrand().id)
            val loginId = signUp()
            productFacade.getProduct(saved.id)

            // act
            likeFacade.like(loginId, saved.id)

            // assert
            assertThat(productFacade.getProduct(saved.id).likeCount).isEqualTo(LikeCount(1))
        }

        @DisplayName("좋아요 취소 뒤에는 줄어든 카운트가 보인다.")
        @Test
        fun showsNewCount_afterUnlike() {
            // arrange
            val saved = saveProduct(brandId = saveBrand().id)
            val loginId = signUp()
            likeFacade.like(loginId, saved.id)
            productFacade.getProduct(saved.id)

            // act
            likeFacade.unlike(loginId, saved.id)

            // assert
            assertThat(productFacade.getProduct(saved.id).likeCount).isEqualTo(LikeCount(0))
        }

        @DisplayName("같은 회원이 두 번 좋아요해도 카운트는 1 이다.")
        @Test
        fun keepsCount_whenLikedTwice() {
            // arrange
            val saved = saveProduct(brandId = saveBrand().id)
            val loginId = signUp()
            likeFacade.like(loginId, saved.id)
            productFacade.getProduct(saved.id)

            // act
            likeFacade.like(loginId, saved.id)

            // assert
            assertThat(productFacade.getProduct(saved.id).likeCount).isEqualTo(LikeCount(1))
        }

        @DisplayName("어드민 상품 수정 뒤에는 새 이름과 가격이 보인다.")
        @Test
        fun showsNewValues_afterProductChange() {
            // arrange
            val saved = saveProduct(brandId = saveBrand().id, name = "옛 이름")
            productFacade.getProduct(saved.id)

            // act
            productAdminFacade.change(
                ProductCommand.Change(id = saved.id, name = ProductName("새 이름"), price = Price(20_000), stock = Stock(10)),
            )

            // assert
            val info = productFacade.getProduct(saved.id)
            assertAll(
                { assertThat(info.name).isEqualTo(ProductName("새 이름")) },
                { assertThat(info.price).isEqualTo(Price(20_000)) },
            )
        }

        @DisplayName("어드민 상품 삭제 뒤에는 404 다.")
        @Test
        fun throwsNotFound_afterProductDelete() {
            // arrange
            val saved = saveProduct(brandId = saveBrand().id)
            productFacade.getProduct(saved.id)

            // act
            productAdminFacade.delete(saved.id)

            // assert
            val result = assertThrows<CoreException> { productFacade.getProduct(saved.id) }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("어드민 브랜드 수정 뒤에는 상세와 목록 모두 새 브랜드 이름이 보인다.")
        @Test
        fun showsNewBrandName_afterBrandChange() {
            // arrange
            val brand = saveBrand("옛 브랜드")
            val saved = saveProduct(brandId = brand.id)
            productFacade.getProduct(saved.id)
            productFacade.getProducts(search())

            // act
            brandAdminFacade.change(
                BrandCommand.Change(id = brand.id, name = BrandName("새 브랜드"), description = BrandDescription.EMPTY),
            )

            // assert
            assertAll(
                { assertThat(productFacade.getProduct(saved.id).brand?.name).isEqualTo(BrandName("새 브랜드")) },
                { assertThat(productFacade.getProducts(search()).content.single().brand?.name).isEqualTo(BrandName("새 브랜드")) },
            )
        }

        @DisplayName("어드민 브랜드 삭제 뒤에는 연쇄 삭제된 상품의 상세가 404 다.")
        @Test
        fun throwsNotFound_afterBrandDelete() {
            // arrange
            val brand = saveBrand()
            val saved = saveProduct(brandId = brand.id)
            productFacade.getProduct(saved.id)

            // act
            brandAdminFacade.delete(brand.id)

            // assert
            val result = assertThrows<CoreException> { productFacade.getProduct(saved.id) }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("어드민 상품 삭제가 롤백되면, 캐시가 남고 그 값은 DB 와 같다.")
        @Test
        fun keepsCacheConsistent_whenDeleteRollsBack() {
            // arrange
            val saved = saveProduct(brandId = saveBrand().id)
            productFacade.getProduct(saved.id)

            // act — Facade 의 @Transactional 이 바깥 트랜잭션에 합류하므로, 삭제 · 무효화 등록이 모두 끝난 뒤 롤백된다
            transactionTemplate.execute { status ->
                productAdminFacade.delete(saved.id)
                status.setRollbackOnly()
            }

            // assert
            val inDb = productRepository.findById(saved.id)
            assertAll(
                { assertThat(inDb).isNotNull() },
                { assertThat(productCache.getProduct(saved.id)).isEqualTo(ProductCacheValue.from(inDb!!)) },
            )
        }
    }

    @DisplayName("좋아요 뒤에 목록을 다시 조회하면, ")
    @Nested
    inner class ListAfterWrite {
        @DisplayName("TTL 동안은 옛 카운트가 보이고, 상세에는 새 카운트가 보인다.")
        @Test
        fun showsOldCountInList_butNewCountInDetail() {
            // arrange
            val saved = saveProduct(brandId = saveBrand().id)
            val loginId = signUp()
            productFacade.getProducts(search())
            productFacade.getProduct(saved.id)

            // act
            likeFacade.like(loginId, saved.id)

            // assert — 목록이 30 초 늦어도 된다는 2026-10-04 결정을 고정한다. 이 단언이 깨지면 그 결정이 바뀐 것이다.
            assertAll(
                { assertThat(productFacade.getProducts(search()).content.single().likeCount).isEqualTo(LikeCount(0)) },
                { assertThat(productFacade.getProduct(saved.id).likeCount).isEqualTo(LikeCount(1)) },
            )
        }
    }
}
