package com.loopers.application.order

import com.loopers.application.coupon.CouponFacade
import com.loopers.application.coupon.CouponInfo
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponName
import com.loopers.domain.coupon.CouponStatus
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.Quantity
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductService
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
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import java.time.ZonedDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class OrderFacadeIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val userService: UserService,
    private val productService: ProductService,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val couponFacade: CouponFacade,
    private val couponJpaRepository: CouponJpaRepository,
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

    private fun saveProduct(name: String = "운동화", price: Long = 10_000, stock: Long = 10): ProductModel {
        val brand = brandRepository.save(BrandModel.create(BrandName("루퍼스")))
        return productRepository.save(
            ProductModel.create(
                brandId = brand.id,
                name = ProductName(name),
                price = Price(price),
                stock = Stock(stock),
            ),
        )
    }

    private fun place(loginId: LoginId, vararg items: Pair<Long, Int>, userCouponId: Long? = null) =
        orderFacade.place(
            OrderCommand.Place(
                loginId = loginId,
                items = items.map { OrderCommand.Item(productId = it.first, quantity = Quantity(it.second)) },
                userCouponId = userCouponId,
            ),
        )

    private fun stockOf(productId: Long): Long = productRepository.findById(productId)!!.stock.value

    private fun issuedCoupon(
        loginId: LoginId,
        type: DiscountType = DiscountType.FIXED_AMOUNT,
        value: Long = 5_000,
        expiresAt: ZonedDateTime = ZonedDateTime.now().plusDays(30),
    ): CouponInfo {
        val policy = couponJpaRepository.save(
            CouponModel.create(
                name = CouponName("테스트 쿠폰"),
                discountType = type,
                discountValue = value,
                expiresAt = expiresAt,
            ),
        )
        return couponFacade.issue(loginId, policy.id)
    }

    private fun statusOf(loginId: LoginId, userCouponId: Long): CouponStatus =
        couponFacade.getUserCoupons(loginId, PageQuery(page = 0, size = 20))
            .content.first { it.id == userCouponId }.status

    @DisplayName("주문할 때, ")
    @Nested
    inner class Place {
        @DisplayName("재고가 요청 수량만큼 줄어든다.")
        @Test
        fun decreasesStock() {
            // arrange
            val user = signUp()
            val product = saveProduct(stock = 10)

            // act
            place(user.loginId, product.id to 3)

            // assert
            assertThat(stockOf(product.id)).isEqualTo(7L)
        }

        @DisplayName("주문 시점의 상품명과 가격이 스냅샷으로 저장된다.")
        @Test
        fun savesSnapshot() {
            // arrange
            val user = signUp()
            val product = saveProduct(name = "운동화", price = 39_000, stock = 5)

            // act
            val order = place(user.loginId, product.id to 2)

            // assert
            val item = order.items.first()
            assertAll(
                { assertThat(item.productName).isEqualTo("운동화") },
                { assertThat(item.unitPrice).isEqualTo(39_000L) },
                { assertThat(item.quantity).isEqualTo(2) },
                { assertThat(order.totalPrice).isEqualTo(78_000L) },
            )
        }

        /**
         * 스냅샷이 스냅샷임을 증명하는 테스트다.
         * 주문 후 상품을 바꿔도 주문서는 그대로여야 한다.
         */
        @DisplayName("주문 뒤 상품 가격이 바뀌어도, 주문의 단가는 그대로다.")
        @Test
        fun keepsSnapshot_whenProductChangesLater() {
            // arrange
            val user = signUp()
            val product = saveProduct(price = 10_000, stock = 5)
            val order = place(user.loginId, product.id to 1)

            // act
            productService.change(
                com.loopers.domain.product.ProductCommand.Change(
                    id = product.id,
                    name = ProductName("이름이 바뀐 상품"),
                    price = Price(99_000),
                    stock = Stock(100),
                ),
            )

            // assert
            val reloaded = orderFacade.getOrder(user.loginId, order.id)
            assertAll(
                { assertThat(reloaded.items.first().unitPrice).isEqualTo(10_000L) },
                { assertThat(reloaded.items.first().productName).isEqualTo("운동화") },
            )
        }

        @DisplayName("여러 상품을 한 번에 주문하면, 각각의 재고가 줄고 총액이 합산된다.")
        @Test
        fun decreasesEveryProductStock() {
            // arrange
            val user = signUp()
            val first = saveProduct(name = "A", price = 1_000, stock = 5)
            val second = saveProduct(name = "B", price = 2_000, stock = 5)

            // act
            val order = place(user.loginId, first.id to 1, second.id to 2)

            // assert
            assertAll(
                { assertThat(stockOf(first.id)).isEqualTo(4L) },
                { assertThat(stockOf(second.id)).isEqualTo(3L) },
                { assertThat(order.totalPrice).isEqualTo(5_000L) },
                { assertThat(order.itemCount).isEqualTo(2) },
            )
        }

        /**
         * 재고 부족은 주문 전체를 실패시킨다.
         * 이 단언의 핵심은 예외가 아니라 "성공한 항목의 재고도 되돌아왔다" 는 쪽이다.
         */
        @DisplayName("한 항목이라도 재고가 모자라면, CONFLICT 가 나고 다른 항목의 재고도 그대로다.")
        @Test
        fun throwsConflict_andRollsBackEverything_whenAnyStockIsInsufficient() {
            // arrange
            val user = signUp()
            val enough = saveProduct(name = "A", stock = 10)
            val notEnough = saveProduct(name = "B", stock = 1)

            // act
            val result = assertThrows<CoreException> { place(user.loginId, enough.id to 1, notEnough.id to 5) }

            // assert
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT) },
                { assertThat(stockOf(enough.id)).isEqualTo(10L) },
                { assertThat(stockOf(notEnough.id)).isEqualTo(1L) },
            )
        }

        @DisplayName("재고와 요청 수량이 같으면, 주문이 성사되고 재고가 0 이 된다.")
        @Test
        fun succeeds_whenStockEqualsQuantity() {
            // arrange
            val user = signUp()
            val product = saveProduct(stock = 3)

            // act
            place(user.loginId, product.id to 3)

            // assert
            assertThat(stockOf(product.id)).isEqualTo(0L)
        }

        @DisplayName("가입되지 않은 로그인 ID 면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenUserDoesNotExist() {
            // arrange
            val product = saveProduct()

            // act
            val result = assertThrows<CoreException> { place(LoginId("nobody"), product.id to 1) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("존재하지 않는 상품이 섞이면, NOT_FOUND 예외가 발생하고 아무 재고도 줄지 않는다.")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            // arrange
            val user = signUp()
            val product = saveProduct(stock = 10)

            // act
            val result = assertThrows<CoreException> { place(user.loginId, product.id to 1, 99999L to 1) }

            // assert
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND) },
                { assertThat(stockOf(product.id)).isEqualTo(10L) },
            )
        }

        @DisplayName("삭제된 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenProductIsSoftDeleted() {
            // arrange
            val user = signUp()
            val product = saveProduct(stock = 10)
            productService.delete(product.id)

            // act
            val result = assertThrows<CoreException> { place(user.loginId, product.id to 1) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("같은 상품을 두 항목으로 보내면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenProductIdIsDuplicated() {
            // arrange
            val user = signUp()
            val product = saveProduct(stock = 10)

            // act
            val result = assertThrows<CoreException> { place(user.loginId, product.id to 1, product.id to 2) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("항목이 비어 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenItemsAreEmpty() {
            // arrange
            val user = signUp()

            // act
            val result = assertThrows<CoreException> { place(user.loginId) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("주문을 단건 조회할 때, ")
    @Nested
    inner class GetOrder {
        @DisplayName("항목까지 채워서 반환된다.")
        @Test
        fun returnsOrderWithItems() {
            // arrange
            val user = signUp()
            val product = saveProduct(name = "운동화", price = 39_000, stock = 5)
            val placed = place(user.loginId, product.id to 2)

            // act
            val found = orderFacade.getOrder(user.loginId, placed.id)

            // assert
            assertAll(
                { assertThat(found.id).isEqualTo(placed.id) },
                { assertThat(found.items).hasSize(1) },
                { assertThat(found.items.first().productName).isEqualTo("운동화") },
                { assertThat(found.items.first().subtotal).isEqualTo(78_000L) },
                { assertThat(found.totalPrice).isEqualTo(78_000L) },
            )
        }

        /**
         * 남의 주문을 403 이 아니라 404 로 막는다. (설계 문서 4.5 장)
         * 403 은 "그 주문은 존재한다" 를 알려주므로, ID 를 훑으면 주문량이 노출된다.
         */
        @DisplayName("다른 회원의 주문이면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenOrderBelongsToAnotherUser() {
            // arrange
            val mine = signUp("loopers01")
            val other = signUp("loopers02")
            val product = saveProduct(stock = 5)
            val placed = place(other.loginId, product.id to 1)

            // act
            val result = assertThrows<CoreException> { orderFacade.getOrder(mine.loginId, placed.id) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("없는 주문이면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenOrderDoesNotExist() {
            // arrange
            val user = signUp()

            // act
            val result = assertThrows<CoreException> { orderFacade.getOrder(user.loginId, 99999L) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("가입되지 않은 로그인 ID 면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenUserDoesNotExist() {
            // act
            val result = assertThrows<CoreException> { orderFacade.getOrder(LoginId("nobody"), 1L) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("주문 목록을 조회할 때, ")
    @Nested
    inner class GetOrders {
        @DisplayName("최근 주문이 앞에 오고, 항목은 담기지 않는다.")
        @Test
        fun ordersByMostRecent_withoutItems() {
            // arrange
            val user = signUp()
            val product = saveProduct(stock = 10)
            val first = place(user.loginId, product.id to 1)
            val second = place(user.loginId, product.id to 1)

            // act
            val result = orderFacade.getOrders(user.loginId, null, null, PageQuery())

            // assert
            assertAll(
                { assertThat(result.content.map { it.id }).containsExactly(second.id, first.id) },
                { assertThat(result.content).allSatisfy { assertThat(it.items).isEmpty() } },
                { assertThat(result.content.first().itemCount).isEqualTo(1) },
            )
        }

        @DisplayName("다른 회원의 주문은 섞이지 않는다.")
        @Test
        fun doesNotMixOtherUsersOrders() {
            // arrange
            val mine = signUp("loopers01")
            val other = signUp("loopers02")
            val product = saveProduct(stock = 10)
            val myOrder = place(mine.loginId, product.id to 1)
            place(other.loginId, product.id to 1)

            // act
            val result = orderFacade.getOrders(mine.loginId, null, null, PageQuery())

            // assert
            assertAll(
                { assertThat(result.content.map { it.id }).containsExactly(myOrder.id) },
                { assertThat(result.totalElements).isEqualTo(1L) },
            )
        }

        @DisplayName("주문이 없으면, 빈 목록과 totalElements 0 이 반환된다.")
        @Test
        fun returnsEmptyPage_whenNoOrders() {
            // arrange
            val user = signUp()

            // act
            val result = orderFacade.getOrders(user.loginId, null, null, PageQuery())

            // assert
            assertAll(
                { assertThat(result.content).isEmpty() },
                { assertThat(result.totalElements).isEqualTo(0L) },
                { assertThat(result.totalPages).isEqualTo(0) },
            )
        }
    }

    @DisplayName("쿠폰을 적용해 주문할 때, ")
    @Nested
    inner class PlaceWithCoupon {
        @DisplayName("정액 쿠폰만큼 결제액이 줄고 쿠폰이 USED 가 된다.")
        @Test
        fun appliesFixedAmountDiscount() {
            // arrange
            val user = signUp()
            val product = saveProduct(price = 10_000, stock = 10)
            val coupon = issuedCoupon(user.loginId, value = 5_000)

            // act
            val info = place(user.loginId, product.id to 2, userCouponId = coupon.id)

            // assert
            assertAll(
                { assertThat(info.totalPrice).describedAs("총액은 할인 전 합계다").isEqualTo(20_000L) },
                { assertThat(info.discountAmount).isEqualTo(5_000L) },
                { assertThat(info.paidAmount).isEqualTo(15_000L) },
                { assertThat(statusOf(user.loginId, coupon.id)).isEqualTo(CouponStatus.USED) },
            )
        }

        @DisplayName("할인이 총액보다 크면, 결제액이 0 원이 된다.")
        @Test
        fun paysZero_whenDiscountExceedsTotal() {
            // arrange
            // 총액 3,000 원에 5,000 원 쿠폰을 쓴다. 초과분은 소멸하며 잔액으로 이월되지 않는다.
            val user = signUp()
            val product = saveProduct(price = 3_000, stock = 10)
            val coupon = issuedCoupon(user.loginId, value = 5_000)

            // act
            val info = place(user.loginId, product.id to 1, userCouponId = coupon.id)

            // assert
            assertAll(
                { assertThat(info.discountAmount).isEqualTo(3_000L) },
                { assertThat(info.paidAmount).isEqualTo(0L) },
            )
        }

        /**
         * 설계 문서 6.1 장의 두 번째 보장이다.
         * 쿠폰을 소모한 뒤 재고 부족으로 예외가 나면 트랜잭션이 롤백되어 쿠폰이 돌아와야 한다.
         * 이것이 깨지면 사용자는 주문도 못 하고 쿠폰도 잃는다.
         */
        @DisplayName("재고가 부족하면, 쿠폰이 미사용 상태로 돌아온다.")
        @Test
        fun restoresCoupon_whenStockIsInsufficient() {
            // arrange
            val user = signUp()
            val product = saveProduct(price = 10_000, stock = 0)
            val coupon = issuedCoupon(user.loginId)

            // act
            val result = assertThrows<CoreException> {
                place(user.loginId, product.id to 1, userCouponId = coupon.id)
            }

            // assert
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT) },
                { assertThat(statusOf(user.loginId, coupon.id)).isEqualTo(CouponStatus.AVAILABLE) },
            )
        }

        @DisplayName("정률 쿠폰이 총액 기준으로 계산된다.")
        @Test
        fun appliesPercentageDiscount_basedOnTotalPrice() {
            // arrange
            val user = signUp()
            val product = saveProduct(price = 10_000, stock = 10)
            val coupon = issuedCoupon(user.loginId, type = DiscountType.PERCENTAGE, value = 10)

            // act
            val info = place(user.loginId, product.id to 2, userCouponId = coupon.id)

            // assert
            assertAll(
                { assertThat(info.discountAmount).isEqualTo(2_000L) },
                { assertThat(info.paidAmount).isEqualTo(18_000L) },
            )
        }

        @DisplayName("같은 쿠폰을 두 번 쓰면, 두 번째는 CONFLICT 다.")
        @Test
        fun throwsConflict_whenCouponReused() {
            // arrange
            val user = signUp()
            val product = saveProduct(price = 10_000, stock = 10)
            val coupon = issuedCoupon(user.loginId)
            place(user.loginId, product.id to 1, userCouponId = coupon.id)

            // act
            val result = assertThrows<CoreException> {
                place(user.loginId, product.id to 1, userCouponId = coupon.id)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("만료된 쿠폰이면, CONFLICT 이고 재고가 줄지 않는다.")
        @Test
        fun throwsConflict_whenCouponExpired() {
            // arrange
            // 쿠폰 판정이 재고 차감보다 앞이라는 설계 문서 6.4 장의 순서 계약이 결과로 드러나는 지점이다.
            val user = signUp()
            val product = saveProduct(price = 10_000, stock = 10)
            val coupon = issuedCoupon(user.loginId, expiresAt = ZonedDateTime.now().minusDays(1))

            // act
            val result = assertThrows<CoreException> {
                place(user.loginId, product.id to 1, userCouponId = coupon.id)
            }

            // assert
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT) },
                { assertThat(stockOf(product.id)).isEqualTo(10L) },
            )
        }

        @DisplayName("남의 쿠폰이면, NOT_FOUND 이고 재고가 줄지 않는다.")
        @Test
        fun throwsNotFound_whenCouponBelongsToAnotherUser() {
            // arrange
            // 쿠폰 판정이 재고 차감보다 앞이라는 설계 문서 6.4 장의 순서 계약이 결과로 드러나는 지점이다.
            val owner = signUp("loopers01")
            val other = signUp("loopers02")
            val product = saveProduct(price = 10_000, stock = 10)
            val coupon = issuedCoupon(owner.loginId)

            // act
            val result = assertThrows<CoreException> {
                place(other.loginId, product.id to 1, userCouponId = coupon.id)
            }

            // assert
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND) },
                { assertThat(stockOf(product.id)).isEqualTo(10L) },
            )
        }

        @DisplayName("쿠폰 없이 주문하면, 기존과 동일하다.")
        @Test
        fun behavesAsBeforeCoupons_whenNoCouponSpecified() {
            // arrange
            val user = signUp()
            val product = saveProduct(price = 10_000, stock = 10)

            // act
            val info = place(user.loginId, product.id to 1)

            // assert
            assertAll(
                { assertThat(info.discountAmount).isEqualTo(0L) },
                { assertThat(info.paidAmount).isEqualTo(info.totalPrice) },
            )
        }
    }
}
