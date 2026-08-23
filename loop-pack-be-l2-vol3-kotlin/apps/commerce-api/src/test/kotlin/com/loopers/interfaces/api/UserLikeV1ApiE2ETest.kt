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
import com.loopers.interfaces.api.product.ProductV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserLikeV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userService: UserService,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val LOGIN_ID = "loopers01"
    }

    private val listResponseType =
        object : ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>>() {}
    private val emptyResponseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

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

    /** loginId 가 null 이면 헤더를 아예 넣지 않는다. */
    private fun getLikes(query: String = "", loginId: String? = LOGIN_ID) =
        testRestTemplate.exchange(
            "/api/v1/users/me/likes$query",
            HttpMethod.GET,
            HttpEntity<Any>(HttpHeaders().apply { loginId?.let { set(ApiHeaders.LOGIN_ID, it) } }),
            listResponseType,
        )

    private fun like(productId: Long, loginId: String = LOGIN_ID) =
        testRestTemplate.exchange(
            "/api/v1/products/$productId/likes",
            HttpMethod.POST,
            HttpEntity<Any>(HttpHeaders().apply { set(ApiHeaders.LOGIN_ID, loginId) }),
            emptyResponseType,
        )

    @DisplayName("GET /api/v1/users/me/likes")
    @Nested
    inner class GetLikedProducts {
        @DisplayName("좋아요한 상품 목록을 200 과 함께 반환한다.")
        @Test
        fun returnsLikedProducts() {
            // arrange
            signUp()
            val product = saveProduct(likeCount = 3)
            like(product.id)

            // act
            val response = getLikes()

            // assert
            val data = response.body?.data
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(data?.content).hasSize(1) },
                { assertThat(data?.content?.first()?.id).isEqualTo(product.id) },
                { assertThat(data?.content?.first()?.likeCount).isEqualTo(4L) },
                { assertThat(data?.content?.first()?.brand?.name).isEqualTo("루퍼스") },
                { assertThat(data?.totalElements).isEqualTo(1L) },
            )
        }

        @DisplayName("파라미터가 없으면, page 0 size 20 이 적용된다.")
        @Test
        fun appliesDefaults_whenNoParameterIsGiven() {
            // arrange
            signUp()

            // act
            val response = getLikes()

            // assert
            val data = response.body?.data
            assertAll(
                { assertThat(data?.page).isEqualTo(0) },
                { assertThat(data?.size).isEqualTo(20) },
            )
        }

        @DisplayName("좋아요가 없으면, 빈 목록과 totalElements 0 을 반환한다.")
        @Test
        fun returnsEmptyPage_whenNothingIsLiked() {
            // arrange
            signUp()

            // act
            val response = getLikes()

            // assert
            val data = response.body?.data
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(data?.content).isEmpty() },
                { assertThat(data?.totalElements).isEqualTo(0L) },
                { assertThat(data?.totalPages).isEqualTo(0) },
            )
        }

        @DisplayName("헤더가 없으면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenHeaderIsMissing() {
            // arrange
            signUp()

            // act
            val response = getLikes(loginId = null)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("가입되지 않은 로그인 ID 면, 404 NOT_FOUND 를 반환한다.")
        @Test
        fun returnsNotFound_whenUserDoesNotExist() {
            // act
            val response = getLikes(loginId = "nobody")

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("페이징 파라미터가 범위를 벗어나면, 400 BAD_REQUEST 를 반환한다.")
        @ParameterizedTest
        @ValueSource(strings = ["?page=-1", "?size=0", "?size=101", "?page=abc"])
        fun returnsBadRequest_whenPagingParameterIsInvalid(query: String) {
            // arrange
            signUp()

            // act
            val response = getLikes(query = query)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        /**
         * Vary 가 없으면 공유 캐시가 A 의 좋아요 목록을 B 에게 그대로 돌려줄 수 있다.
         * URL 이 모든 사용자에게 동일하기 때문이다.
         */
        @DisplayName("응답에 no-store 와 Vary 헤더가 실린다.")
        @Test
        fun setsCacheHeaders() {
            // arrange
            signUp()

            // act
            val response = getLikes()

            // assert
            assertAll(
                { assertThat(response.headers.getFirst("Cache-Control")).isEqualTo("no-store") },
                { assertThat(response.headers.getFirst("Vary")).isEqualTo("X-Loopers-LoginId") },
            )
        }
    }
}
