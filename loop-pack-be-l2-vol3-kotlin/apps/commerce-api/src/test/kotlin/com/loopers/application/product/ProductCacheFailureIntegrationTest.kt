package com.loopers.application.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.admin.brand.BrandAdminFacade
import com.loopers.application.admin.product.ProductAdminFacade
import com.loopers.application.like.LikeFacade
import com.loopers.domain.brand.BrandCommand
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
import com.loopers.infrastructure.cache.RedisCacheOperations
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.net.ServerSocket
import java.time.Duration

/**
 * Redis 가 죽어 있어도 상품 조회 · 좋아요 · 어드민 쓰기가 DB 만으로 정상 동작함을 Facade 수준에서 증명한다.
 * (2026-10-04 상품 캐시 설계 7, 10 장)
 *
 * 장애는 컨테이너를 멈추지 않고 만든다. 컨테이너는 다른 테스트와 공유하므로 멈추면 전체가 흔들린다.
 * 대신 닫힌 포트를 가리키는 템플릿으로 만든 RedisCacheOperations 를 @Primary 로 바꿔 끼운다.
 * 이 클래스만 별도 컨텍스트를 쓰고, 다른 테스트의 컨텍스트에는 영향이 없다.
 */
@SpringBootTest
@Import(ProductCacheFailureIntegrationTest.UnreachableRedisConfig::class)
class ProductCacheFailureIntegrationTest @Autowired constructor(
    private val productFacade: ProductFacade,
    private val productCache: ProductCache,
    private val likeFacade: LikeFacade,
    private val productAdminFacade: ProductAdminFacade,
    private val brandAdminFacade: BrandAdminFacade,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val userService: UserService,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    /**
     * 열었다 닫은 포트로 접속하는 연산을 기본 RedisCacheOperations 대신 사용하게 한다.
     *
     * 커넥션 팩토리를 빈으로 등록하지 않는다. 등록하면 LettuceConnectionFactory 후보가 늘어나고,
     * 기본 팩토리가 @Primary 라 파라미터 이름이 아니라 기본 팩토리가 주입되어 장애가 주입되지 않는다.
     */
    @TestConfiguration
    class UnreachableRedisConfig : DisposableBean {
        private val connectionFactory = LettuceConnectionFactory(
            RedisStandaloneConfiguration("localhost", ServerSocket(0).use { it.localPort }),
            LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).build(),
        ).apply { afterPropertiesSet() }

        @Bean
        @Primary
        fun unreachableRedisCacheOperations(objectMapper: ObjectMapper): RedisCacheOperations {
            val template = RedisTemplate<String, String>().apply {
                // apply 안의 connectionFactory 는 템플릿 자신의 프로퍼티(null)로 해석되므로 설정 클래스의 필드를 명시한다.
                setConnectionFactory(this@UnreachableRedisConfig.connectionFactory)
                keySerializer = StringRedisSerializer()
                valueSerializer = StringRedisSerializer()
                afterPropertiesSet()
            }
            return RedisCacheOperations(template, template, objectMapper)
        }

        override fun destroy() = connectionFactory.destroy()
    }

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

    private fun search(brandId: Long? = null, sort: ProductSortType = ProductSortType.LATEST) =
        ProductCriteria.Search(brandId = brandId, sort = sort, pageQuery = PageQuery(0, 20))

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("Redis 에 접속할 수 없을 때, ")
    @Nested
    inner class WhenRedisIsDown {
        @DisplayName("상품 상세는 DB 값과 브랜드 정보로 정상 반환되고, 캐시에는 아무것도 남지 않는다.")
        @Test
        fun returnsDetailFromDb() {
            // arrange
            val brand = saveBrand("루퍼스")
            val saved = saveProduct(brandId = brand.id, name = "베이직 티셔츠", likeCount = 3)

            // act
            val info = productFacade.getProduct(saved.id)

            // assert — 두 번째 단언은 장애 주입이 실제로 적용됐는지(저장이 성공했다면 캐시에 값이 있었을 것) 확인한다
            assertAll(
                { assertThat(info.name).isEqualTo(ProductName("베이직 티셔츠")) },
                { assertThat(info.likeCount).isEqualTo(LikeCount(3)) },
                { assertThat(info.brand?.name).isEqualTo(BrandName("루퍼스")) },
                { assertThat(productCache.getProduct(saved.id)).isNull() },
            )
        }

        @DisplayName("상품 목록은 brandId 필터와 좋아요 많은 순 정렬을 지켜 정상 반환된다.")
        @Test
        fun returnsListFromDb_withFilterAndSort() {
            // arrange
            val target = saveBrand("대상 브랜드")
            val other = saveBrand("다른 브랜드")
            saveProduct(brandId = target.id, name = "적게", likeCount = 1)
            saveProduct(brandId = target.id, name = "많이", likeCount = 9)
            saveProduct(brandId = other.id, name = "제외", likeCount = 100)

            // act
            val page = productFacade.getProducts(search(brandId = target.id, sort = ProductSortType.LIKES_DESC))

            // assert
            assertAll(
                { assertThat(page.content.map { it.name }).containsExactly(ProductName("많이"), ProductName("적게")) },
                { assertThat(page.content).allSatisfy { assertThat(it.brand?.name).isEqualTo(BrandName("대상 브랜드")) } },
                { assertThat(page.totalElements).isEqualTo(2) },
            )
        }

        @DisplayName("없는 상품은 여전히 404 다.")
        @Test
        fun throwsNotFound_whenProductMissing() {
            // act
            val result = assertThrows<CoreException> { productFacade.getProduct(99999L) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("좋아요와 취소는 예외 없이 커밋되고, 이후 조회에 새 카운트가 보인다.")
        @Test
        fun commitsLikeAndUnlike_andShowsNewCount() {
            // arrange
            val saved = saveProduct(brandId = saveBrand().id)
            val loginId = signUp()

            // act & assert
            assertDoesNotThrow { likeFacade.like(loginId, saved.id) }
            assertThat(productFacade.getProduct(saved.id).likeCount).isEqualTo(LikeCount(1))

            assertDoesNotThrow { likeFacade.unlike(loginId, saved.id) }
            assertThat(productFacade.getProduct(saved.id).likeCount).isEqualTo(LikeCount(0))
        }

        @DisplayName("어드민 상품 수정은 성공하고, 이후 조회에 새 값이 보인다.")
        @Test
        fun commitsProductChange() {
            // arrange
            val saved = saveProduct(brandId = saveBrand().id, name = "옛 이름")

            // act
            assertDoesNotThrow {
                productAdminFacade.change(
                    ProductCommand.Change(id = saved.id, name = ProductName("새 이름"), price = Price(20_000), stock = Stock(10)),
                )
            }

            // assert
            val info = productFacade.getProduct(saved.id)
            assertAll(
                { assertThat(info.name).isEqualTo(ProductName("새 이름")) },
                { assertThat(info.price).isEqualTo(Price(20_000)) },
            )
        }

        @DisplayName("어드민 상품 삭제와 브랜드 수정 · 삭제도 성공하고, 이후 조회에 반영된다.")
        @Test
        fun commitsProductDeleteAndBrandWrites() {
            // arrange
            val brand = saveBrand("옛 브랜드")
            val kept = saveProduct(brandId = brand.id, name = "남는 상품")
            val removed = saveProduct(brandId = brand.id, name = "지울 상품")

            // act & assert — 상품 삭제
            assertDoesNotThrow { productAdminFacade.delete(removed.id) }
            assertThat(assertThrows<CoreException> { productFacade.getProduct(removed.id) }.errorType)
                .isEqualTo(ErrorType.NOT_FOUND)

            // act & assert — 브랜드 수정
            assertDoesNotThrow {
                brandAdminFacade.change(
                    BrandCommand.Change(id = brand.id, name = BrandName("새 브랜드"), description = BrandDescription.EMPTY),
                )
            }
            assertThat(productFacade.getProduct(kept.id).brand?.name).isEqualTo(BrandName("새 브랜드"))

            // act & assert — 브랜드 삭제는 연쇄 삭제된 상품을 404 로 만든다
            assertDoesNotThrow { brandAdminFacade.delete(brand.id) }
            assertThat(assertThrows<CoreException> { productFacade.getProduct(kept.id) }.errorType)
                .isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
