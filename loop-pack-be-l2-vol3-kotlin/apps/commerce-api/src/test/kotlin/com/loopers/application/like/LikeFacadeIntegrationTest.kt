package com.loopers.application.like

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.LikeCount
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class LikeFacadeIntegrationTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val userService: UserService,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
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

    private fun saveProduct(likeCount: Long = 0): ProductModel {
        val brand = brandRepository.save(BrandModel.create(BrandName("루퍼스")))
        return productRepository.save(
            ProductModel.create(
                brandId = brand.id,
                name = ProductName("상품"),
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
}
