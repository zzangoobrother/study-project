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
import com.loopers.infrastructure.like.ProductLikeJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 좋아요의 동시성 계약을 지키는 회귀 테스트.
 *
 * Testcontainers 가 띄우는 진짜 MySQL 8.0 위에서 돌기 때문에 InnoDB 의 행 락과 유니크 제약이 실제로 동작한다.
 * 인메모리 DB 였다면 이 검증이 불가능했을 것이다.
 *
 * 각 테스트가 좋아요 행 수와 like_count 를 함께 단언하는 이유는,
 * 두 진실 원천이 서로 어긋나지 않았는지가 확인 대상이기 때문이다. (설계 문서 6.1 장)
 */
@SpringBootTest
class LikeFacadeConcurrencyTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val userService: UserService,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val productLikeJpaRepository: ProductLikeJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val CONCURRENT_USERS = 10
        private const val BASE_LIKE_COUNT = 5L
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun signUp(loginId: String): UserModel =
        userService.signUp(
            UserCommand.SignUp(
                loginId = LoginId(loginId),
                password = RawPassword("Loopers1!"),
                name = UserName("홍길동"),
                birthDate = BirthDate.from("1990-01-01"),
                email = Email("$loginId@loopers.com"),
            ),
        )

    private fun saveProduct(): ProductModel {
        val brand = brandRepository.save(BrandModel.create(BrandName("루퍼스")))
        return productRepository.save(
            ProductModel.create(
                brandId = brand.id,
                name = ProductName("상품"),
                price = Price(10_000),
                likeCount = LikeCount(BASE_LIKE_COUNT),
            ),
        )
    }

    private fun likeCountOf(productId: Long): Long = productRepository.findById(productId)!!.likeCount.value

    /**
     * 모든 스레드를 같은 순간에 출발시킨다.
     * 순차 실행이면 경합이 재현되지 않아 테스트가 있으나 마나가 되므로 시작 래치가 필요하다.
     */
    private fun runConcurrently(count: Int, task: (Int) -> Unit) {
        val executor = Executors.newFixedThreadPool(count)
        val ready = CountDownLatch(count)
        val start = CountDownLatch(1)
        val done = CountDownLatch(count)
        val failures = CopyOnWriteArrayList<Throwable>()

        repeat(count) { index ->
            executor.submit {
                ready.countDown()
                start.await()
                try {
                    task(index)
                } catch (e: Throwable) {
                    failures.add(e)
                } finally {
                    done.countDown()
                }
            }
        }

        ready.await(10, TimeUnit.SECONDS)
        start.countDown()
        done.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        // 예외가 밖으로 새면 설계가 흡수하기로 한 경합을 흡수하지 못한 것이다. (설계 문서 6.8 장)
        assertThat(failures).describedAs("동시 요청에서 예외가 밖으로 새면 안 된다").isEmpty()
    }

    /**
     * 원자적 UPDATE 가 아니라 "엔티티를 읽어 +1" 이면 이 단언이 실패한다.
     * 여러 스레드가 같은 값을 읽고 같은 값을 써서 증가분이 사라진다.
     */
    @DisplayName("서로 다른 회원이 같은 상품에 동시에 좋아요하면, 좋아요 수가 회원 수만큼 늘어난다.")
    @Test
    fun countsEveryLike_whenDifferentUsersLikeConcurrently() {
        // arrange
        val users = (1..CONCURRENT_USERS).map { signUp("user$it") }
        val product = saveProduct()

        // act
        runConcurrently(CONCURRENT_USERS) { index -> likeFacade.like(users[index].loginId, product.id) }

        // assert
        assertAll(
            { assertThat(likeCountOf(product.id)).isEqualTo(BASE_LIKE_COUNT + CONCURRENT_USERS) },
            { assertThat(productLikeJpaRepository.count()).isEqualTo(CONCURRENT_USERS.toLong()) },
        )
    }

    /**
     * 유니크 제약이 없거나 경합 예외를 흡수하지 못하면 이 단언이 실패한다.
     * 행이 둘 생기거나, 진 쪽의 예외가 밖으로 새어 failures 가 비지 않는다.
     */
    @DisplayName("같은 회원이 같은 상품에 동시에 좋아요를 두 번 보내면, 행 하나와 수 1 증가만 남는다.")
    @Test
    fun keepsSingleRow_whenSameUserLikesConcurrently() {
        // arrange
        val user = signUp("loopers01")
        val product = saveProduct()

        // act
        runConcurrently(2) { likeFacade.like(user.loginId, product.id) }

        // assert
        assertAll(
            { assertThat(likeCountOf(product.id)).isEqualTo(BASE_LIKE_COUNT + 1) },
            { assertThat(productLikeJpaRepository.count()).isEqualTo(1L) },
        )
    }

    /**
     * 취소를 "읽고 → deletedAt 확인 → delete()" 로 하면 이 단언이 실패한다.
     * 두 스레드가 모두 살아 있는 행을 보고 각자 수를 1 씩 줄여 2 가 빠진다. (설계 문서 6.2 장)
     */
    @DisplayName("같은 회원이 같은 상품의 좋아요를 동시에 두 번 취소해도, 수는 1 만 줄어든다.")
    @Test
    fun decreasesOnce_whenSameUserUnlikesConcurrently() {
        // arrange
        val user = signUp("loopers01")
        val product = saveProduct()
        likeFacade.like(user.loginId, product.id)

        // act
        runConcurrently(2) { likeFacade.unlike(user.loginId, product.id) }

        // assert
        assertThat(likeCountOf(product.id)).isEqualTo(BASE_LIKE_COUNT)
    }
}
