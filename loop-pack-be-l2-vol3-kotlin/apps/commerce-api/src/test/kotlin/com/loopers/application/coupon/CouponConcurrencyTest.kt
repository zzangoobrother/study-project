package com.loopers.application.coupon

import com.loopers.application.order.OrderFacade
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponName
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.Quantity
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.Stock
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserName
import com.loopers.domain.user.UserService
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.infrastructure.coupon.UserCouponJpaRepository
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 쿠폰의 동시성 계약을 지키는 회귀 테스트.
 *
 * "재사용 불가" 는 조건부 UPDATE 의 WHERE c.usedAt IS NULL 하나에 걸려 있다. 누군가 이것을
 * "읽고 → 확인하고 → 쓰기" 로 바꿔도 단일 스레드 테스트는 전부 통과한다 — 이 파일만이 그 회귀를 잡는다.
 *
 * Testcontainers 가 띄우는 진짜 MySQL 8.0 위에서 돌기 때문에 InnoDB 의 행 락이 실제로 동작한다.
 * 인메모리 DB 였다면 이 검증이 불가능했을 것이다.
 */
@SpringBootTest
class CouponConcurrencyTest @Autowired constructor(
    private val couponFacade: CouponFacade,
    private val orderFacade: OrderFacade,
    private val userService: UserService,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val couponJpaRepository: CouponJpaRepository,
    private val userCouponJpaRepository: UserCouponJpaRepository,
    private val orderJpaRepository: OrderJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val CONCURRENT_REQUESTS = 10
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

    private fun saveProduct(name: String = "상품", stock: Long): ProductModel {
        val brand = brandRepository.save(BrandModel.create(BrandName("루퍼스")))
        return productRepository.save(
            ProductModel.create(
                brandId = brand.id,
                name = ProductName(name),
                price = Price(1_000),
                stock = Stock(stock),
            ),
        )
    }

    private fun savedCoupon(expiresAt: ZonedDateTime = ZonedDateTime.now().plusDays(30)): CouponModel =
        couponJpaRepository.save(
            CouponModel.create(
                name = CouponName("테스트 쿠폰"),
                discountType = DiscountType.FIXED,
                discountValue = 5_000,
                expiresAt = expiresAt,
            ),
        )

    private fun place(loginId: LoginId, vararg items: Pair<Long, Int>, userCouponId: Long? = null) =
        orderFacade.place(
            OrderCommand.Place(
                loginId = loginId,
                items = items.map { OrderCommand.Item(productId = it.first, quantity = Quantity(it.second)) },
                userCouponId = userCouponId,
            ),
        )

    private fun usedAtOf(userCouponId: Long): ZonedDateTime? =
        userCouponJpaRepository.findById(userCouponId).get().usedAt

    private fun userCouponCountOf(userId: Long): Long =
        userCouponJpaRepository.countByUserIdAndDeletedAtIsNull(userId)

    private fun stockOf(productId: Long): Long = productRepository.findById(productId)!!.stock.value

    /**
     * OrderRepository 에는 count 가 없고 findAll 은 OrderCriteria.Search 를 요구한다.
     * DatabaseCleanUp 이 매 테스트 truncate 하므로 전체 건수가 곧 이 테스트의 건수다.
     */
    private fun orderCount(): Long = orderJpaRepository.count()

    /**
     * 실패가 전부 CoreException 인지 먼저 확인한 뒤 에러 타입만 꺼낸다.
     *
     * filterIsInstance 로 곧장 걸러내면 부분집합만 보게 되어, 일부만 CoreException 으로 변환되고
     * 나머지가 raw 예외로 새는 회귀를 놓친다. 건수 단언도 이 구멍을 막지 못한다 —
     * 그것은 실패의 개수만 보고 타입은 보지 않기 때문이다. 걸러내기 전에 전부인지 확인하는 것이 요점이다.
     */
    private fun errorTypesOf(failures: List<Throwable>): List<ErrorType> {
        assertThat(failures)
            .describedAs("실패가 전부 CoreException 이어야 한다. raw 예외가 섞이면 예외 변환이 빠진 것이다")
            .hasOnlyElementsOfType(CoreException::class.java)
        return failures.filterIsInstance<CoreException>().map { it.errorType }
    }

    /**
     * 모든 스레드를 같은 순간에 출발시킨다.
     * 순차 실행이면 경합이 재현되지 않아 테스트가 있으나 마나가 되므로 시작 래치가 필요하다.
     *
     * 실패를 삼키지 않고 모아서 돌려준다. 어떤 테스트는 실패가 정확히 몇 건이어야 하므로,
     * 판정은 호출자가 한다. (OrderFacadeConcurrencyTest 와 동일한 헬퍼)
     */
    private fun runConcurrently(count: Int, task: (Int) -> Unit): List<Throwable> {
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

        return failures.toList()
    }

    /**
     * 재사용 방지의 본체다.
     * 조건부 UPDATE 의 WHERE used_at IS NULL 을 빼면 여러 주문이 같은 쿠폰으로 성사된다.
     * "읽고 → 확인하고 → 쓰기" 로 되돌려도 마찬가지로 깨진다.
     */
    @DisplayName("같은 쿠폰으로 동시에 여러 번 주문하면, 정확히 한 건만 성사되고 나머지는 CONFLICT 다.")
    @Test
    fun usesCouponExactlyOnce_whenOrderedConcurrently() {
        // arrange — 재고를 넉넉히 두어 실패 원인이 쿠폰뿐이도록 한다
        val user = signUp("loopers1")
        val product = saveProduct(stock = CONCURRENT_REQUESTS.toLong())
        val coupon = couponFacade.issue(user.loginId, savedCoupon().id)

        // act
        val failures = runConcurrently(CONCURRENT_REQUESTS) {
            place(user.loginId, product.id to 1, userCouponId = coupon.id)
        }

        // assert
        assertAll(
            {
                assertThat(failures)
                    .describedAs("한 건만 성사되어야 하므로 나머지는 전부 실패한다")
                    .hasSize(CONCURRENT_REQUESTS - 1)
            },
            {
                assertThat(errorTypesOf(failures))
                    .describedAs("실패는 전부 쿠폰 재사용이어야 한다")
                    .containsOnly(ErrorType.CONFLICT)
            },
            {
                assertThat(usedAtOf(coupon.id))
                    .describedAs("쿠폰은 정확히 한 번만 소모되어야 한다")
                    .isNotNull()
            },
            {
                assertThat(orderCount())
                    .describedAs("성사된 주문은 정확히 1 건이다")
                    .isEqualTo(1L)
            },
            {
                assertThat(stockOf(product.id))
                    .describedAs("성사된 1 건만큼만 재고가 줄어야 한다")
                    .isEqualTo((CONCURRENT_REQUESTS - 1).toLong())
            },
        )
    }

    /**
     * 유니크 제약이 최종 방어선이다.
     * 서비스의 중복 검사만으로는 두 요청이 모두 "없음" 을 보는 경우가 남는다. (설계 문서 6.6 장)
     */
    @DisplayName("같은 정책으로 동시에 여러 번 발급하면, 한 건만 성공하고 행은 하나다.")
    @Test
    fun issuesExactlyOnce_whenRequestedConcurrently() {
        // arrange
        val user = signUp("loopers1")
        val policy = savedCoupon()

        // act
        val failures = runConcurrently(CONCURRENT_REQUESTS) { couponFacade.issue(user.loginId, policy.id) }

        // assert
        assertAll(
            { assertThat(failures).hasSize(CONCURRENT_REQUESTS - 1) },
            {
                assertThat(errorTypesOf(failures))
                    .describedAs("중복 발급은 전부 CONFLICT 여야 한다. 500 이 섞이면 예외 변환이 빠진 것이다")
                    .containsOnly(ErrorType.CONFLICT)
            },
            {
                assertThat(userCouponCountOf(user.id))
                    .describedAs("유니크 제약이 행을 하나로 유지해야 한다")
                    .isEqualTo(1L)
            },
        )
    }

    /**
     * 조건부 UPDATE 와 트랜잭션 롤백이 만나는 지점이다.
     *
     * 스레드 하나가 쿠폰 UPDATE 를 이기고 재고에서 실패해 롤백하는 사이, 진 스레드들은
     * "이미 사용됨" 을 본다. 전원이 실패하는 것까지는 단일 스레드 테스트도 보이지만,
     * 롤백된 쿠폰이 실제로 다시 쓰이는지는 이 테스트만 증명한다.
     */
    @DisplayName("재고가 모자라 동시 주문이 전부 실패해도, 쿠폰은 미사용으로 남아 다시 쓸 수 있다.")
    @Test
    fun keepsCouponReusable_whenConcurrentOrdersAllFailOnStock() {
        // arrange
        val user = signUp("loopers1")
        val soldOut = saveProduct(name = "품절", stock = 0)
        val inStock = saveProduct(name = "재고있음", stock = 1)
        val coupon = couponFacade.issue(user.loginId, savedCoupon().id)

        // act — 전부 재고에서 실패한다
        val failures = runConcurrently(CONCURRENT_REQUESTS) {
            place(user.loginId, soldOut.id to 1, userCouponId = coupon.id)
        }

        // assert
        assertAll(
            {
                assertThat(failures)
                    .describedAs("재고가 0 이므로 한 건도 성사되지 않는다")
                    .hasSize(CONCURRENT_REQUESTS)
            },
            {
                assertThat(errorTypesOf(failures))
                    .describedAs("재고 부족과 쿠폰 경합 모두 CONFLICT 다")
                    .containsOnly(ErrorType.CONFLICT)
            },
            { assertThat(orderCount()).describedAs("성사된 주문이 없다").isEqualTo(0L) },
            {
                assertThat(usedAtOf(coupon.id))
                    .describedAs("쿠폰 소모가 전부 롤백되어야 한다")
                    .isNull()
            },
            {
                // 롤백이 상태만 되돌린 게 아니라 실제로 다시 쓰이는지 확인한다
                assertThat(place(user.loginId, inStock.id to 1, userCouponId = coupon.id).discountAmount)
                    .describedAs("복구된 쿠폰이 실제로 적용되어야 한다")
                    .isEqualTo(1_000L)
            },
        )
    }
}
