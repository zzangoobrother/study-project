package com.loopers.interfaces.api

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
import com.loopers.domain.user.UserName
import com.loopers.domain.user.UserService
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductLikeV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userService: UserService,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val LOGIN_ID = "loopers01"
    }

    private val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun signUp(loginId: String = LOGIN_ID) =
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

    /** loginId 가 null 이면 헤더를 아예 넣지 않는다. UserV1ApiE2ETest 와 같은 방식이다. */
    private fun request(method: HttpMethod, productId: Any, loginId: String? = LOGIN_ID) =
        testRestTemplate.exchange(
            "/api/v1/products/$productId/likes",
            method,
            HttpEntity<Any>(HttpHeaders().apply { loginId?.let { set(ApiHeaders.LOGIN_ID, it) } }),
            responseType,
        )

    private fun likeCountOf(productId: Long): Long = productRepository.findById(productId)!!.likeCount.value

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    inner class Like {
        @DisplayName("좋아요에 성공하면, 200 을 반환하고 좋아요 수가 1 늘어난다.")
        @Test
        fun returnsOk_andIncreasesLikeCount() {
            // arrange
            signUp()
            val product = saveProduct(likeCount = 5)

            // act
            val response = request(HttpMethod.POST, product.id)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(likeCountOf(product.id)).isEqualTo(6L) },
            )
        }

        @DisplayName("이미 좋아요한 상품에 다시 요청해도, 200 이고 좋아요 수는 그대로다.")
        @Test
        fun returnsOk_andKeepsLikeCount_whenRequestedTwice() {
            // arrange
            signUp()
            val product = saveProduct(likeCount = 5)
            request(HttpMethod.POST, product.id)

            // act
            val response = request(HttpMethod.POST, product.id)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(likeCountOf(product.id)).isEqualTo(6L) },
            )
        }

        @DisplayName("헤더가 없으면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenHeaderIsMissing() {
            // arrange
            signUp()
            val product = saveProduct()

            // act
            val response = request(HttpMethod.POST, product.id, loginId = null)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("로그인 ID 형식이 잘못되면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenLoginIdFormatIsInvalid() {
            // arrange
            val product = saveProduct()

            // act
            val response = request(HttpMethod.POST, product.id, loginId = "loopers-01")

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("가입되지 않은 로그인 ID 면, 404 NOT_FOUND 를 반환한다.")
        @Test
        fun returnsNotFound_whenUserDoesNotExist() {
            // arrange
            val product = saveProduct()

            // act
            val response = request(HttpMethod.POST, product.id, loginId = "nobody")

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("존재하지 않는 상품이면, 404 NOT_FOUND 를 반환한다.")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            // arrange
            signUp()

            // act
            val response = request(HttpMethod.POST, 99999L)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("상품 ID 가 숫자가 아니면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenProductIdIsNotNumeric() {
            // arrange
            signUp()

            // act
            val response = request(HttpMethod.POST, "abc")

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    inner class Unlike {
        @DisplayName("취소에 성공하면, 200 을 반환하고 좋아요 수가 1 줄어든다.")
        @Test
        fun returnsOk_andDecreasesLikeCount() {
            // arrange
            signUp()
            val product = saveProduct(likeCount = 5)
            request(HttpMethod.POST, product.id)

            // act
            val response = request(HttpMethod.DELETE, product.id)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(likeCountOf(product.id)).isEqualTo(5L) },
            )
        }

        @DisplayName("좋아요하지 않은 상품을 취소해도, 200 이고 좋아요 수는 그대로다.")
        @Test
        fun returnsOk_andKeepsLikeCount_whenNotLiked() {
            // arrange
            signUp()
            val product = saveProduct(likeCount = 5)

            // act
            val response = request(HttpMethod.DELETE, product.id)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(likeCountOf(product.id)).isEqualTo(5L) },
            )
        }

        @DisplayName("헤더가 없으면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenHeaderIsMissing() {
            // arrange
            signUp()
            val product = saveProduct()

            // act
            val response = request(HttpMethod.DELETE, product.id, loginId = null)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("존재하지 않는 상품이면, 404 NOT_FOUND 를 반환한다.")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            // arrange
            signUp()

            // act
            val response = request(HttpMethod.DELETE, 99999L)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }
}
