package com.loopers.application.order

import com.loopers.application.coupon.CouponFacade
import com.loopers.application.payment.PaymentFacade
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponName
import com.loopers.domain.coupon.CouponStatus
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.order.Quantity
import com.loopers.domain.payment.CardNo
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentCommand
import com.loopers.domain.payment.PaymentGateway
import com.loopers.domain.payment.PgRequestResult
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.Stock
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserName
import com.loopers.domain.user.UserService
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.infrastructure.coupon.UserCouponJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class OrderExpirationFacadeIntegrationTest @Autowired constructor(
    private val orderExpirationFacade: OrderExpirationFacade,
    private val orderFacade: OrderFacade,
    private val paymentFacade: PaymentFacade,
    private val orderService: OrderService,
    private val userService: UserService,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val productService: ProductService,
    private val couponFacade: CouponFacade,
    private val couponJpaRepository: CouponJpaRepository,
    private val userCouponJpaRepository: UserCouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockitoBean
    lateinit var paymentGateway: PaymentGateway

    companion object {
        private const val LOGIN_ID = "loopers01"
        private const val KEY = "20251013:TR:abc123"
        private val THIRTY_MINUTES: Duration = Duration.ofMinutes(30)
    }

    private val loginId = LoginId(LOGIN_ID)
    private lateinit var productA: ProductModel
    private lateinit var productB: ProductModel

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun setUp() {
        userService.signUp(
            UserCommand.SignUp(
                loginId = loginId,
                password = RawPassword("Loopers1!"),
                name = UserName("홍길동"),
                birthDate = BirthDate.from("1990-01-01"),
                email = Email("$LOGIN_ID@loopers.com"),
            ),
        )
        val brand = brandRepository.save(BrandModel.create(BrandName("루퍼스")))
        productA = saveProduct(brand.id, "운동화")
        productB = saveProduct(brand.id, "슬리퍼")
    }

    private fun saveProduct(brandId: Long, name: String): ProductModel =
        productRepository.save(
            ProductModel.create(brandId = brandId, name = ProductName(name), price = Price(10_000), stock = Stock(10)),
        )

    private fun place(vararg items: Pair<Long, Int>, couponId: Long? = null): Long =
        orderFacade.place(
            OrderCommand.Place(
                loginId = loginId,
                items = items.map { OrderCommand.Item(productId = it.first, quantity = Quantity(it.second)) },
                couponId = couponId,
            ),
        ).id

    /**
     * 정책을 하나 더 먼저 만들어 정책 ID 와 발급(user_coupons) ID 를 어긋나게 한다.
     * truncate 뒤에는 둘 다 1 이라, 복구가 정책 ID 로 UPDATE 하는 회귀를 테스트가 알아채지 못한다.
     */
    private fun issueCoupon(value: Long = 5_000, expiresAt: ZonedDateTime = ZonedDateTime.now().plusDays(30)): Long {
        saveCouponPolicy(value = 1_000, expiresAt = expiresAt)
        val policy = saveCouponPolicy(value = value, expiresAt = expiresAt)
        couponFacade.issue(loginId, policy.id)
        return policy.id
    }

    private fun saveCouponPolicy(value: Long, expiresAt: ZonedDateTime): CouponModel =
        couponJpaRepository.save(
            CouponModel.create(
                name = CouponName("테스트 쿠폰"),
                discountType = DiscountType.FIXED,
                discountValue = value,
                expiresAt = expiresAt,
            ),
        )

    private fun payCommand(orderId: Long) = PaymentCommand.Request(
        loginId = loginId,
        orderId = orderId,
        cardType = CardType.SAMSUNG,
        cardNo = CardNo("1234-5678-9814-1451"),
    )

    /** 저장 후 다시 읽은 값이다. 메모리 값은 마이크로초 반올림 때문에 경계 테스트를 흔든다. */
    private fun createdAtOf(orderId: Long): ZonedDateTime = orderService.getOrder(orderId)!!.createdAt

    private fun statusOf(orderId: Long) = orderService.getOrder(orderId)!!.status

    private fun stockOf(productId: Long): Long = productRepository.findById(productId)!!.stock.value

    private fun userCouponOf(orderId: Long) =
        userCouponJpaRepository.findById(orderService.getOrder(orderId)!!.usedCouponId!!).get()

    private fun couponUsedAtOf(orderId: Long): ZonedDateTime? = userCouponOf(orderId).usedAt

    @DisplayName("만료 시각이 지난 주문을 처리할 때, ")
    @Nested
    inner class ExpireOverdue {
        @DisplayName("결제 대기 주문은 취소하고 항목별 재고와 쿠폰을 되돌린다.")
        @Test
        fun cancelsPendingOrder_andRestoresStockAndCoupon() {
            // arrange
            setUp()
            val couponId = issueCoupon()
            val orderId = place(productA.id to 2, productB.id to 3, couponId = couponId)
            val stockAfterOrder = listOf(stockOf(productA.id), stockOf(productB.id))
            val usedBefore = couponUsedAtOf(orderId)
            val usedCouponId = orderService.getOrder(orderId)!!.usedCouponId

            // act
            val count = orderExpirationFacade.expireOverdue(createdAtOf(orderId).plus(THIRTY_MINUTES))

            // assert
            assertAll(
                { assertThat(stockAfterOrder).containsExactly(8L, 7L) },
                { assertThat(usedBefore).isNotNull() },
                { assertThat(usedCouponId).isNotEqualTo(couponId) },
                { assertThat(count).isEqualTo(1) },
                { assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CANCELED) },
                { assertThat(stockOf(productA.id)).isEqualTo(10L) },
                { assertThat(stockOf(productB.id)).isEqualTo(10L) },
                { assertThat(couponUsedAtOf(orderId)).isNull() },
            )
        }

        @DisplayName("결제에 실패한 주문도 취소하고 재고를 되돌린다.")
        @Test
        fun cancelsPaymentFailedOrder() {
            // arrange
            setUp()
            val orderId = place(productA.id to 2)
            whenever(paymentGateway.request(any())).thenReturn(PgRequestResult.NotSent)
            paymentFacade.request(payCommand(orderId))
            val failed = statusOf(orderId)

            // act
            orderExpirationFacade.expireOverdue(createdAtOf(orderId).plus(THIRTY_MINUTES))

            // assert
            assertAll(
                { assertThat(failed).isEqualTo(OrderStatus.PAYMENT_FAILED) },
                { assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CANCELED) },
                { assertThat(stockOf(productA.id)).isEqualTo(10L) },
            )
        }

        @DisplayName("쿠폰 없는 주문도 예외 없이 취소하고 재고를 되돌린다.")
        @Test
        fun cancelsOrderWithoutCoupon() {
            // arrange
            setUp()
            val orderId = place(productA.id to 2)

            // act
            orderExpirationFacade.expireOverdue(createdAtOf(orderId).plus(THIRTY_MINUTES))

            // assert
            assertAll(
                { assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CANCELED) },
                { assertThat(stockOf(productA.id)).isEqualTo(10L) },
            )
        }

        @DisplayName("정확히 30 분이 된 주문은 취소한다.")
        @Test
        fun expiresAtExactlyThirtyMinutes() {
            // arrange
            setUp()
            val orderId = place(productA.id to 2)

            // act
            val count = orderExpirationFacade.expireOverdue(createdAtOf(orderId).plus(THIRTY_MINUTES))

            // assert
            assertAll(
                { assertThat(count).isEqualTo(1) },
                { assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CANCELED) },
            )
        }

        @DisplayName("30 분 1 초 전인 주문은 그대로 둔다.")
        @Test
        fun keepsOrderOneSecondBeforeThirtyMinutes() {
            // arrange
            setUp()
            val orderId = place(productA.id to 2)

            // act
            val count = orderExpirationFacade.expireOverdue(createdAtOf(orderId).plus(THIRTY_MINUTES).minusSeconds(1))

            // assert
            assertAll(
                { assertThat(count).isZero() },
                { assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAYMENT_PENDING) },
                { assertThat(stockOf(productA.id)).isEqualTo(8L) },
            )
        }

        @DisplayName("결제 중이거나 결제 완료인 주문은 시간이 지나도 그대로 둔다.")
        @Test
        fun keepsPayingAndPaidOrders() {
            // arrange
            setUp()
            val payingId = place(productA.id to 2)
            whenever(paymentGateway.request(any())).thenReturn(PgRequestResult.Unknown)
            paymentFacade.request(payCommand(payingId))
            // 쿠폰이 총액을 모두 할인하면 PG 를 거치지 않고 바로 PAID 가 된다.
            val paidId = place(productB.id to 1, couponId = issueCoupon(value = 100_000))
            paymentFacade.request(payCommand(paidId))

            // act
            val count = orderExpirationFacade.expireOverdue(createdAtOf(paidId).plus(Duration.ofHours(1)))

            // assert
            assertAll(
                { assertThat(count).isZero() },
                { assertThat(statusOf(payingId)).isEqualTo(OrderStatus.PAYING) },
                { assertThat(statusOf(paidId)).isEqualTo(OrderStatus.PAID) },
                { assertThat(stockOf(productA.id)).isEqualTo(8L) },
                { assertThat(stockOf(productB.id)).isEqualTo(9L) },
            )
        }

        @DisplayName("소프트 삭제된 상품이 섞인 주문도 취소하고 삭제된 상품의 재고까지 되돌린다.")
        @Test
        fun restoresStock_whenOrderContainsSoftDeletedProduct() {
            // arrange
            setUp()
            val orderId = place(productA.id to 2, productB.id to 3)
            productService.delete(productA.id)

            // act
            orderExpirationFacade.expireOverdue(createdAtOf(orderId).plus(THIRTY_MINUTES))

            // assert
            assertAll(
                { assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CANCELED) },
                { assertThat(productService.getProductIncludingDeleted(productA.id)!!.stock.value).isEqualTo(10L) },
                { assertThat(stockOf(productB.id)).isEqualTo(10L) },
            )
        }

        @DisplayName("사용 시점엔 유효했던 쿠폰이 지금 만료됐어도 사용 이력을 되돌린다.")
        @Test
        fun restoresCoupon_whenCouponHasSinceExpired() {
            // arrange
            setUp()
            val couponId = issueCoupon(expiresAt = ZonedDateTime.now().plusHours(1))
            val orderId = place(productA.id to 2, couponId = couponId)
            val expiresAt = userCouponOf(orderId).expiresAt

            // act
            orderExpirationFacade.expireOverdue(createdAtOf(orderId).plus(THIRTY_MINUTES))

            // assert
            val coupon = userCouponOf(orderId)
            assertAll(
                { assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CANCELED) },
                { assertThat(coupon.usedAt).isNull() },
                { assertThat(coupon.statusAt(expiresAt.plusSeconds(1))).isEqualTo(CouponStatus.EXPIRED) },
            )
        }

        @DisplayName("두 번 실행해도 재고는 한 번만 돌아온다.")
        @Test
        fun restoresOnlyOnce_whenRunTwice() {
            // arrange
            setUp()
            val orderId = place(productA.id to 2)
            val now = createdAtOf(orderId).plus(THIRTY_MINUTES)

            // act
            val first = orderExpirationFacade.expireOverdue(now)
            val second = orderExpirationFacade.expireOverdue(now)

            // assert
            assertAll(
                { assertThat(first).isEqualTo(1) },
                { assertThat(second).isZero() },
                { assertThat(stockOf(productA.id)).isEqualTo(10L) },
            )
        }
    }

    @DisplayName("결제 시작과 만료가 겹칠 때, ")
    @Nested
    inner class Race {
        /**
         * 두 트랜잭션이 매번 겹친다는 보장은 없다. 그래서 어느 쪽이 이겨도 성립하는 불변식만 단언한다.
         * 정확히 하나만 이기는 근거는 같은 행 · 같은 기대 상태 집합의 조건부 UPDATE 다. (2026-10-28 주문 만료 설계 4.2 장)
         */
        @DisplayName("결제 시작과 만료 중 정확히 하나만 이긴다.")
        @Test
        fun onlyOneOfPaymentAndExpirationWins() {
            // arrange
            setUp()
            val orderId = place(productA.id to 2)
            whenever(paymentGateway.request(any())).thenReturn(PgRequestResult.Accepted(KEY))
            val executor = Executors.newFixedThreadPool(2)
            val ready = CountDownLatch(2)
            val start = CountDownLatch(1)

            // act
            val payment = executor.submit<Result<Any>> {
                ready.countDown()
                start.await()
                runCatching { paymentFacade.request(payCommand(orderId)) }
            }
            val expiration = executor.submit<Boolean> {
                ready.countDown()
                start.await()
                orderExpirationFacade.expire(orderId)
            }
            ready.await()
            start.countDown()
            val paymentResult = payment.get(10, TimeUnit.SECONDS)
            val expired = expiration.get(10, TimeUnit.SECONDS)
            executor.shutdown()

            // assert
            val status = statusOf(orderId)
            if (expired) {
                val error = paymentResult.exceptionOrNull() as? CoreException
                assertAll(
                    { assertThat(status).isEqualTo(OrderStatus.CANCELED) },
                    { assertThat(stockOf(productA.id)).isEqualTo(10L) },
                    { assertThat(error?.errorType).isEqualTo(ErrorType.CONFLICT) },
                )
            } else {
                assertAll(
                    { assertThat(status).isEqualTo(OrderStatus.PAYING) },
                    { assertThat(stockOf(productA.id)).isEqualTo(8L) },
                    { assertThat(paymentResult.isSuccess).isTrue() },
                )
            }
        }

        @DisplayName("같은 주문의 만료 둘이 겹치면 한쪽만 취소하고 재고는 한 번만 돌아온다.")
        @Test
        fun restoresOnlyOnce_whenTwoExpirationsRace() {
            // arrange
            setUp()
            val orderId = place(productA.id to 2)
            val executor = Executors.newFixedThreadPool(2)
            val ready = CountDownLatch(2)
            val start = CountDownLatch(1)

            // act
            val futures = (1..2).map {
                executor.submit<Boolean> {
                    ready.countDown()
                    start.await()
                    orderExpirationFacade.expire(orderId)
                }
            }
            ready.await()
            start.countDown()
            val results = futures.map { it.get(10, TimeUnit.SECONDS) }
            executor.shutdown()

            // assert
            assertAll(
                { assertThat(results.count { it }).isEqualTo(1) },
                { assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CANCELED) },
                { assertThat(stockOf(productA.id)).isEqualTo(10L) },
            )
        }
    }
}
