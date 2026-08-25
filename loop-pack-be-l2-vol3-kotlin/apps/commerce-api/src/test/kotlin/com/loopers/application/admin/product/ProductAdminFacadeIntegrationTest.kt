package com.loopers.application.admin.product

import com.loopers.application.like.LikeFacade
import com.loopers.domain.brand.BrandDescription
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.LikeCount
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductCommand
import com.loopers.domain.product.ProductCriteria
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.Stock
import com.loopers.domain.support.PageQuery
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserName
import com.loopers.domain.user.UserService
import com.loopers.infrastructure.like.ProductLikeJpaRepository
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
class ProductAdminFacadeIntegrationTest @Autowired constructor(
    private val productAdminFacade: ProductAdminFacade,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val likeFacade: LikeFacade,
    private val userService: UserService,
    private val productLikeJpaRepository: ProductLikeJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private fun saveBrand(name: String = "루퍼스"): BrandModel =
        brandRepository.save(BrandModel.create(BrandName(name), BrandDescription("일상을 조금 낫게")))

    private fun saveProduct(brandId: Long, name: String = "운동화"): ProductModel =
        productRepository.save(ProductModel.create(brandId = brandId, name = ProductName(name), price = Price(39000)))

    private fun signUp(loginId: String = "loopers01"): UserModel =
        userService.signUp(
            UserCommand.SignUp(
                loginId = LoginId(loginId),
                password = RawPassword("Loopers1!"),
                name = UserName("홍길동"),
                birthDate = BirthDate.from("1990-01-01"),
                email = Email("$loginId@loopers.com"),
            ),
        )

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("상품을 단건 조회할 때, ")
    @Nested
    inner class GetProduct {
        @DisplayName("브랜드 정보가 함께 채워진다.")
        @Test
        fun fillsBrand() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)

            // act
            val info = productAdminFacade.getProduct(product.id)

            // assert
            assertAll(
                { assertThat(info.id).isEqualTo(product.id) },
                { assertThat(info.brand?.id).isEqualTo(brand.id) },
                { assertThat(info.brand?.name).isEqualTo(BrandName("루퍼스")) },
                { assertThat(info.deleted).isFalse() },
            )
        }

        /**
         * 공개 API 는 삭제된 브랜드를 brand = null 로 표현하지만 어드민은 그러면 안 된다.
         * "브랜드가 삭제됨" 과 "브랜드를 알 수 없음" 이 같은 표현으로 뭉개지기 때문이다.
         */
        @DisplayName("브랜드가 삭제됐어도 브랜드 정보가 채워지고 brand.deleted 가 true 다.")
        @Test
        fun fillsDeletedBrand() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)
            brand.delete()
            brandRepository.save(brand)

            // act
            val info = productAdminFacade.getProduct(product.id)

            // assert
            assertAll(
                { assertThat(info.brand?.id).isEqualTo(brand.id) },
                { assertThat(info.brand?.deleted).isTrue() },
            )
        }

        @DisplayName("삭제된 상품도 반환되고 deleted 가 true 다.")
        @Test
        fun returnsDeletedProduct() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)
            product.delete()
            productRepository.save(product)

            // act
            val info = productAdminFacade.getProduct(product.id)

            // assert
            assertThat(info.deleted).isTrue()
        }

        @DisplayName("브랜드가 아예 없는 상품이면, brand 가 null 이다.")
        @Test
        fun returnsNullBrand_whenBrandDoesNotExist() {
            // arrange
            val product = saveProduct(brandId = 99999L)

            // act
            val info = productAdminFacade.getProduct(product.id)

            // assert
            assertThat(info.brand).isNull()
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 를 던진다.")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            // act & assert
            assertThatThrownBy { productAdminFacade.getProduct(99999L) }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("상품 목록을 조회할 때, ")
    @Nested
    inner class GetProducts {
        @DisplayName("삭제된 상품도 포함되고 브랜드가 조합된다.")
        @Test
        fun includesDeletedProductsWithBrand() {
            // arrange
            val brand = saveBrand()
            saveProduct(brand.id, name = "운동화")
            val deleted = saveProduct(brand.id, name = "러닝화")
            deleted.delete()
            productRepository.save(deleted)

            // act
            val page = productAdminFacade.getProducts(
                ProductCriteria.AdminSearch(brandId = null, pageQuery = PageQuery(0, 20)),
            )

            // assert
            assertAll(
                { assertThat(page.content).hasSize(2) },
                { assertThat(page.content.map { it.deleted }).containsExactly(true, false) },
                { assertThat(page.content.map { it.brand?.id }).containsOnly(brand.id) },
            )
        }

        @DisplayName("brandId 로 필터하면, 해당 브랜드의 상품만 반환된다.")
        @Test
        fun filtersByBrandId() {
            // arrange
            val target = saveBrand(name = "루퍼스")
            val other = saveBrand(name = "몬드리안")
            val targetProduct = saveProduct(target.id, name = "운동화")
            saveProduct(other.id, name = "러닝화")

            // act
            val page = productAdminFacade.getProducts(
                ProductCriteria.AdminSearch(brandId = target.id, pageQuery = PageQuery(0, 20)),
            )

            // assert
            assertThat(page.content.map { it.id }).containsExactly(targetProduct.id)
        }

        /**
         * includesDeletedProductsWithBrand 는 브랜드가 살아있어 loadBrands 가 getBrands(삭제 제외)로
         * 바뀌어도 통과해버린다. 목록 경로에서 삭제된 브랜드가 실제로 채워지는지는 이 테스트만 검증한다.
         */
        @DisplayName("브랜드가 삭제됐어도 목록에서 브랜드 정보가 채워지고 brand.deleted 가 true 다.")
        @Test
        fun fillsDeletedBrand() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)
            brand.delete()
            brandRepository.save(brand)

            // act
            val page = productAdminFacade.getProducts(
                ProductCriteria.AdminSearch(brandId = null, pageQuery = PageQuery(0, 20)),
            )

            // assert
            assertAll(
                { assertThat(page.content).hasSize(1) },
                { assertThat(page.content.first().id).isEqualTo(product.id) },
                { assertThat(page.content.first().brand?.id).isEqualTo(brand.id) },
                { assertThat(page.content.first().brand?.deleted).isTrue() },
            )
        }
    }

    @DisplayName("상품을 등록할 때, ")
    @Nested
    inner class Register {
        @DisplayName("등록되고 좋아요 수는 0 이며 브랜드가 채워진다.")
        @Test
        fun registersProduct() {
            // arrange
            val brand = saveBrand()

            // act
            val info = productAdminFacade.register(
                ProductCommand.Register(brandId = brand.id, name = ProductName("운동화"), price = Price(39000), stock = Stock.ZERO),
            )

            // assert
            assertAll(
                { assertThat(info.id).isPositive() },
                { assertThat(info.likeCount).isEqualTo(LikeCount.ZERO) },
                { assertThat(info.brand?.id).isEqualTo(brand.id) },
                { assertThat(info.deleted).isFalse() },
            )
        }

        /**
         * "없는 브랜드" 와 "삭제된 브랜드" 가 한 번에 걸린다.
         * brandService.getBrand 가 이미 삭제를 제외하는 조회이므로 null 하나로 두 경우가 표현되고 둘 다 400 이다.
         */
        @DisplayName("존재하지 않는 브랜드면, BAD_REQUEST 를 던진다.")
        @Test
        fun throwsBadRequest_whenBrandDoesNotExist() {
            // act & assert
            assertThatThrownBy {
                productAdminFacade.register(
                    ProductCommand.Register(brandId = 99999L, name = ProductName("운동화"), price = Price(39000), stock = Stock.ZERO),
                )
            }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("삭제된 브랜드면, BAD_REQUEST 를 던진다.")
        @Test
        fun throwsBadRequest_whenBrandIsDeleted() {
            // arrange
            val brand = saveBrand()
            brand.delete()
            brandRepository.save(brand)

            // act & assert
            assertThatThrownBy {
                productAdminFacade.register(
                    ProductCommand.Register(brandId = brand.id, name = ProductName("운동화"), price = Price(39000), stock = Stock.ZERO),
                )
            }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("재고와 함께 등록하면, 그 재고가 저장된다.")
        @Test
        fun savesStock_whenRegistered() {
            // arrange
            val brand = saveBrand()

            // act
            val info = productAdminFacade.register(
                ProductCommand.Register(
                    brandId = brand.id,
                    name = ProductName("운동화"),
                    price = Price(39_000),
                    stock = Stock(50),
                ),
            )

            // assert — info.stock 은 ProductAdminInfo.of() 매핑을, 재조회는 실제 저장을 각각 검증한다
            assertAll(
                { assertThat(info.stock).isEqualTo(Stock(50)) },
                { assertThat(productRepository.findById(info.id)!!.stock.value).isEqualTo(50L) },
            )
        }
    }

    @DisplayName("상품을 수정하고 삭제할 때, ")
    @Nested
    inner class ChangeAndDelete {
        @DisplayName("수정하면 이름과 가격이 교체되고 브랜드는 유지된다.")
        @Test
        fun changesProduct() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)

            // act
            val info = productAdminFacade.change(
                ProductCommand.Change(id = product.id, name = ProductName("러닝화"), price = Price(59000), stock = Stock.ZERO),
            )

            // assert
            assertAll(
                { assertThat(info.name).isEqualTo(ProductName("러닝화")) },
                { assertThat(info.price).isEqualTo(Price(59000)) },
                { assertThat(info.brand?.id).isEqualTo(brand.id) },
            )
        }

        @DisplayName("삭제하면 이후 조회에서 deleted 가 true 다.")
        @Test
        fun deletesProduct() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)

            // act
            productAdminFacade.delete(product.id)

            // assert
            assertThat(productAdminFacade.getProduct(product.id).deleted).isTrue()
        }

        @DisplayName("상품을 삭제하면, 그 상품의 좋아요도 함께 삭제된다.")
        @Test
        fun softDeletesLikes_whenProductIsDeleted() {
            // arrange
            val user = signUp()
            val product = saveProduct(saveBrand().id)
            likeFacade.like(user.loginId, product.id)

            // act
            productAdminFacade.delete(product.id)

            // assert — 행은 남지만 살아 있는 좋아요는 0 이다
            assertThat(productLikeJpaRepository.findAll().single().deletedAt).isNotNull()
        }

        /**
         * PUT 은 전체 교체이므로 재고도 교체 대상이다. (설계 문서 5.6 장)
         * 주문에 의한 차감은 이 경로를 타지 않는다 — 그쪽은 조건부 UPDATE 다.
         */
        @DisplayName("수정하면, 재고도 함께 교체된다.")
        @Test
        fun replacesStock_whenChanged() {
            // arrange
            val brand = saveBrand()
            val product = saveProduct(brand.id)

            // act
            val info = productAdminFacade.change(
                ProductCommand.Change(
                    id = product.id,
                    name = ProductName("운동화"),
                    price = Price(39_000),
                    stock = Stock(7),
                ),
            )

            // assert — info.stock 은 ProductAdminInfo.of() 매핑을, 재조회는 실제 저장을 각각 검증한다
            assertAll(
                { assertThat(info.stock).isEqualTo(Stock(7)) },
                { assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(7L) },
            )
        }
    }
}
