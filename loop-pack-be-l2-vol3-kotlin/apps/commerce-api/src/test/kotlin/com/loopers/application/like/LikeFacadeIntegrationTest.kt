package com.loopers.application.like

import com.loopers.application.admin.product.ProductAdminFacade
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.LikeCount
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.support.PageQuery
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserName
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class LikeFacadeIntegrationTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val userService: UserService,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val productAdminFacade: ProductAdminFacade,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

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

    private fun saveProduct(likeCount: Long = 0, brandId: Long? = null, name: String = "상품"): ProductModel {
        val resolvedBrandId = brandId ?: brandRepository.save(BrandModel.create(BrandName("루퍼스"))).id
        return productRepository.save(
            ProductModel.create(
                brandId = resolvedBrandId,
                name = ProductName(name),
                price = Price(10_000),
                likeCount = LikeCount(likeCount),
            ),
        )
    }

    private fun likeCountOf(productId: Long): Long =
        productRepository.findById(productId)!!.likeCount.value

    @DisplayName("좋아요를 등록할 때, ")
    @Nested
    inner class Like {
        @DisplayName("좋아요 수가 1 늘어난다.")
        @Test
        fun increasesLikeCountByOne() {
            // arrange
            val user = signUp()
            val product = saveProduct(likeCount = 5)

            // act
            likeFacade.like(user.loginId, product.id)

            // assert
            assertThat(likeCountOf(product.id)).isEqualTo(6L)
        }

        /**
         * 이 단언이 이 기능의 핵심이다. 중복 등록이 수를 올리면 사용자가 버튼을 두 번 눌러
         * 좋아요 수를 마음대로 부풀릴 수 있다.
         */
        @DisplayName("이미 좋아요한 상품이면, 예외 없이 수가 그대로다.")
        @Test
        fun keepsLikeCount_whenAlreadyLiked() {
            // arrange
            val user = signUp()
            val product = saveProduct(likeCount = 5)
            likeFacade.like(user.loginId, product.id)

            // act
            likeFacade.like(user.loginId, product.id)

            // assert
            assertThat(likeCountOf(product.id)).isEqualTo(6L)
        }

        @DisplayName("취소한 뒤 다시 등록하면, 수가 정확히 복구된다.")
        @Test
        fun restoresLikeCount_whenLikedAgainAfterUnlike() {
            // arrange
            val user = signUp()
            val product = saveProduct(likeCount = 5)
            likeFacade.like(user.loginId, product.id)
            likeFacade.unlike(user.loginId, product.id)

            // act
            likeFacade.like(user.loginId, product.id)

            // assert
            assertThat(likeCountOf(product.id)).isEqualTo(6L)
        }

        @DisplayName("서로 다른 회원이 같은 상품을 좋아요하면, 각각 1 씩 늘어난다.")
        @Test
        fun countsEachUserSeparately() {
            // arrange
            val first = signUp("loopers01")
            val second = signUp("loopers02")
            val product = saveProduct(likeCount = 0)

            // act
            likeFacade.like(first.loginId, product.id)
            likeFacade.like(second.loginId, product.id)

            // assert
            assertThat(likeCountOf(product.id)).isEqualTo(2L)
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            // arrange
            val user = signUp()

            // act
            val result = assertThrows<CoreException> { likeFacade.like(user.loginId, 99999L) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenProductIsSoftDeleted() {
            // arrange
            val user = signUp()
            val product = saveProduct()
            product.delete()
            productRepository.save(product)

            // act
            val result = assertThrows<CoreException> { likeFacade.like(user.loginId, product.id) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("가입되지 않은 로그인 ID 면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenUserDoesNotExist() {
            // arrange
            val product = saveProduct()

            // act
            val result = assertThrows<CoreException> { likeFacade.like(LoginId("nobody"), product.id) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("좋아요를 취소할 때, ")
    @Nested
    inner class Unlike {
        @DisplayName("좋아요 수가 1 줄어든다.")
        @Test
        fun decreasesLikeCountByOne() {
            // arrange
            val user = signUp()
            val product = saveProduct(likeCount = 5)
            likeFacade.like(user.loginId, product.id)

            // act
            likeFacade.unlike(user.loginId, product.id)

            // assert
            assertThat(likeCountOf(product.id)).isEqualTo(5L)
        }

        @DisplayName("좋아요하지 않은 상품이면, 예외 없이 수가 그대로다.")
        @Test
        fun keepsLikeCount_whenNotLiked() {
            // arrange
            val user = signUp()
            val product = saveProduct(likeCount = 5)

            // act
            likeFacade.unlike(user.loginId, product.id)

            // assert
            assertThat(likeCountOf(product.id)).isEqualTo(5L)
        }

        @DisplayName("이미 취소한 상품을 다시 취소해도, 수가 더 줄지 않는다.")
        @Test
        fun keepsLikeCount_whenUnlikedTwice() {
            // arrange
            val user = signUp()
            val product = saveProduct(likeCount = 5)
            likeFacade.like(user.loginId, product.id)
            likeFacade.unlike(user.loginId, product.id)

            // act
            likeFacade.unlike(user.loginId, product.id)

            // assert
            assertThat(likeCountOf(product.id)).isEqualTo(5L)
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            // arrange
            val user = signUp()

            // act
            val result = assertThrows<CoreException> { likeFacade.unlike(user.loginId, 99999L) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("내가 좋아요한 상품 목록을 조회할 때, ")
    @Nested
    inner class GetLikedProducts {
        @DisplayName("좋아요한 상품만 반환된다.")
        @Test
        fun returnsOnlyLikedProducts() {
            // arrange
            val user = signUp()
            val liked = saveProduct(name = "좋아요한 상품")
            saveProduct(name = "좋아요하지 않은 상품")
            likeFacade.like(user.loginId, liked.id)

            // act
            val result = likeFacade.getLikedProducts(user.loginId, PageQuery())

            // assert
            assertAll(
                { assertThat(result.content).hasSize(1) },
                { assertThat(result.content.first().id).isEqualTo(liked.id) },
                { assertThat(result.totalElements).isEqualTo(1L) },
            )
        }

        @DisplayName("최근에 좋아요한 상품이 앞에 온다.")
        @Test
        fun ordersByMostRecentlyLiked() {
            // arrange
            val user = signUp()
            val first = saveProduct(name = "먼저")
            val second = saveProduct(name = "나중")
            likeFacade.like(user.loginId, first.id)
            likeFacade.like(user.loginId, second.id)

            // act
            val result = likeFacade.getLikedProducts(user.loginId, PageQuery())

            // assert
            assertThat(result.content.map { it.id }).containsExactly(second.id, first.id)
        }

        /**
         * created_at 으로 정렬하면 이 단언이 실패한다.
         * 취소했다 다시 누른 좋아요의 created_at 은 최초 시점이라 방금 누른 상품이 맨 뒤로 간다.
         * (설계 문서 4.5 장)
         */
        @DisplayName("취소했다 다시 좋아요한 상품이, 목록 맨 앞에 온다.")
        @Test
        fun putsRelikedProductFirst() {
            // arrange
            val user = signUp()
            val first = saveProduct(name = "먼저")
            val second = saveProduct(name = "나중")
            likeFacade.like(user.loginId, first.id)
            likeFacade.like(user.loginId, second.id)
            likeFacade.unlike(user.loginId, first.id)
            likeFacade.like(user.loginId, first.id)

            // act
            val result = likeFacade.getLikedProducts(user.loginId, PageQuery())

            // assert
            assertThat(result.content.map { it.id }).containsExactly(first.id, second.id)
        }

        @DisplayName("취소한 상품은 목록에서 빠지고, totalElements 도 함께 줄어든다.")
        @Test
        fun excludesUnlikedProduct() {
            // arrange
            val user = signUp()
            val product = saveProduct()
            likeFacade.like(user.loginId, product.id)
            likeFacade.unlike(user.loginId, product.id)

            // act
            val result = likeFacade.getLikedProducts(user.loginId, PageQuery())

            // assert
            assertAll(
                { assertThat(result.content).isEmpty() },
                { assertThat(result.totalElements).isEqualTo(0L) },
            )
        }

        @DisplayName("다른 회원의 좋아요는 섞이지 않는다.")
        @Test
        fun doesNotMixOtherUsersLikes() {
            // arrange
            val mine = signUp("loopers01")
            val other = signUp("loopers02")
            val myProduct = saveProduct(name = "내 것")
            val otherProduct = saveProduct(name = "남의 것")
            likeFacade.like(mine.loginId, myProduct.id)
            likeFacade.like(other.loginId, otherProduct.id)

            // act
            val result = likeFacade.getLikedProducts(mine.loginId, PageQuery())

            // assert
            assertThat(result.content.map { it.id }).containsExactly(myProduct.id)
        }

        @DisplayName("페이징 메타가 좋아요 개수를 기준으로 채워진다.")
        @Test
        fun fillsPagingMetadata() {
            // arrange
            val user = signUp()
            val brandId = brandRepository.save(BrandModel.create(BrandName("루퍼스"))).id
            repeat(5) { index ->
                val product = saveProduct(brandId = brandId, name = "상품${index + 1}")
                likeFacade.like(user.loginId, product.id)
            }

            // act
            val result = likeFacade.getLikedProducts(user.loginId, PageQuery(page = 1, size = 2))

            // assert
            assertAll(
                { assertThat(result.content).hasSize(2) },
                { assertThat(result.page).isEqualTo(1) },
                { assertThat(result.size).isEqualTo(2) },
                { assertThat(result.totalElements).isEqualTo(5L) },
                { assertThat(result.totalPages).isEqualTo(3) },
            )
        }

        @DisplayName("좋아요가 하나도 없으면, 빈 목록과 totalElements 0 이 반환된다.")
        @Test
        fun returnsEmptyPage_whenNothingIsLiked() {
            // arrange
            val user = signUp()

            // act
            val result = likeFacade.getLikedProducts(user.loginId, PageQuery())

            // assert
            assertAll(
                { assertThat(result.content).isEmpty() },
                { assertThat(result.totalElements).isEqualTo(0L) },
                { assertThat(result.totalPages).isEqualTo(0) },
            )
        }

        @DisplayName("상품의 브랜드가 삭제됐으면, brand 는 null 이고 목록에서 빠지지 않는다.")
        @Test
        fun keepsProductWithNullBrand_whenBrandIsSoftDeleted() {
            // arrange
            val user = signUp()
            val brand = brandRepository.save(BrandModel.create(BrandName("루퍼스")))
            val product = saveProduct(brandId = brand.id)
            likeFacade.like(user.loginId, product.id)
            brand.delete()
            brandRepository.save(brand)

            // act
            val result = likeFacade.getLikedProducts(user.loginId, PageQuery())

            // assert
            assertAll(
                { assertThat(result.content).hasSize(1) },
                { assertThat(result.content.first().brand).isNull() },
            )
        }

        @DisplayName("가입되지 않은 로그인 ID 면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenUserDoesNotExist() {
            // act
            val result = assertThrows<CoreException> { likeFacade.getLikedProducts(LoginId("nobody"), PageQuery()) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        /**
         * 연쇄 삭제가 없으면 content 는 비는데 totalElements 는 1 로 남아 응답이 자기모순에 빠진다.
         */
        @DisplayName("좋아요한 상품이 삭제되면, 목록에서 빠지고 totalElements 도 함께 줄어든다.")
        @Test
        fun excludesDeletedProduct_andShrinksTotalElements() {
            // arrange
            val user = signUp()
            val product = saveProduct()
            likeFacade.like(user.loginId, product.id)

            // act
            productAdminFacade.delete(product.id)
            val result = likeFacade.getLikedProducts(user.loginId, PageQuery())

            // assert
            assertAll(
                { assertThat(result.content).isEmpty() },
                { assertThat(result.totalElements).isEqualTo(0L) },
            )
        }
    }
}
