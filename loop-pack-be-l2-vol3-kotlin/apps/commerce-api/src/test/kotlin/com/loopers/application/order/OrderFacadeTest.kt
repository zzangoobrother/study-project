package com.loopers.application.order

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponName
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderItemModel
import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.Quantity
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.Stock
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.EncodedPassword
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.PasswordEncoder
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserName
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.ZonedDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils

/**
 * OrderFacade 의 순수 단위 테스트. UserService / ProductService / OrderService 를 목으로 대체해 DB 없이
 * 협력자 호출의 순서 · 인자 · 횟수만 본다. DB 최종 상태를 보는 OrderFacadeIntegrationTest 와 보완 관계다.
 *
 * LikeServiceTest 와 같은 이유로 목을 쓴다 — 이 테스트의 핵심 단언 다수가
 * "특정 메서드를 특정 순서로 호출했다/하지 않았다" 이며, 손으로 만든 페이크로는 호출 스파이를 새로 심어야 한다.
 */
class OrderFacadeTest {
    companion object {
        private const val USER_ID = 1L
        private val LOGIN_ID = LoginId("loopers1")
    }

    private val userService = mock<UserService>()
    private val productService = mock<ProductService>()
    private val orderService = mock<OrderService>()
    private val couponService = mock<CouponService>()
    private val orderFacade = OrderFacade(userService, productService, orderService, couponService)

    /**
     * BaseEntity.id 는 `val id: Long = 0` 이라 영속화하지 않은 엔티티는 전부 id 가 0 이다.
     * OrderFacade.loadProductsOrThrow 가 associateBy { it.id } 로 맵을 만들고
     * place() 가 products.getValue(item.productId) 로 꺼내므로, 상품이 둘 이상이면
     * id 를 심지 않고는 키 충돌(0 으로 통일)이나 NoSuchElementException 을 피할 수 없다.
     * 리플렉션으로 진짜 엔티티에 id 만 주입해 이 문제를 없앤다.
     */
    private fun <T : Any> T.withId(id: Long): T = apply { ReflectionTestUtils.setField(this, "id", id) }

    /**
     * BaseEntity.createdAt 은 lateinit var 이고 @PrePersist 에서만 채워진다.
     * OrderInfo.of(model) 이 model.createdAt 을 읽으므로, place() 가 성공하는 모든 케이스에서
     * 목이 반환하는 OrderModel 에 createdAt 을 심어두지 않으면 UninitializedPropertyAccessException 이 난다.
     */
    private fun <T : Any> T.withCreatedAt(createdAt: ZonedDateTime = ZonedDateTime.now()): T =
        apply { ReflectionTestUtils.setField(this, "createdAt", createdAt) }

    /**
     * UserModel.create 는 private 생성자를 가려 PasswordEncoder 를 통해서만 만들 수 있다.
     * 이 테스트는 인코딩 결과를 검증하지 않으므로 목 인코더로 아무 값이나 돌려주면 충분하다.
     * getUserOrThrow 가 돌려준 user.id 가 orderService.place(userId = ...) 로 그대로 전달되므로
     * id 도 리플렉션으로 심는다.
     *
     * 주의 — 호출부에서 `whenever(userService.getUser(...)).thenReturn(user())` 처럼 인자 자리에서
     * 바로 부르면 안 된다. 이 함수 안의 `whenever(passwordEncoder...).thenReturn(...)` 가 완결되기 전에
     * 바깥 whenever 의 스텁이 아직 진행 중이라, Mockito 가 두 스텁이 겹친 것으로 보고
     * UnfinishedStubbingException 을 던진다. 항상 `val u = user()` 로 먼저 받은 뒤 넘긴다.
     */
    private fun user(id: Long = USER_ID, loginId: LoginId = LOGIN_ID): UserModel {
        val passwordEncoder = mock<PasswordEncoder>()
        whenever(passwordEncoder.encode(any())).thenReturn(EncodedPassword("encoded"))
        return UserModel.create(
            loginId = loginId,
            rawPassword = RawPassword("Loopers1!"),
            name = UserName("홍길동"),
            birthDate = BirthDate.from("1990-01-01"),
            email = Email("user$id@loopers.com"),
            passwordEncoder = passwordEncoder,
        ).withId(id)
    }

    private fun product(id: Long, name: String = "상품$id", price: Long = 1_000L): ProductModel =
        ProductModel.create(
            brandId = 1L,
            name = ProductName(name),
            price = Price(price),
            stock = Stock(100L),
        ).withId(id)

    private fun orderItem(productId: Long, quantity: Int = 1): OrderItemModel =
        OrderItemModel.create(
            productId = productId,
            productName = ProductName("상품$productId"),
            unitPrice = Price(1_000L),
            quantity = Quantity(quantity),
        )

    /** orderService.place 목이 돌려줄 값. 실제 계산 로직(totalPrice 등)을 태우기 위해 진짜 엔티티를 쓴다. */
    private fun order(userId: Long = USER_ID, items: List<OrderItemModel> = listOf(orderItem(1L))): OrderModel =
        OrderModel.create(userId = userId, items = items).withId(100L).withCreatedAt()

    @DisplayName("주문할 때, ")
    @Nested
    inner class Place {
        @DisplayName("요청 순서와 무관하게 재고는 상품 ID 오름차순으로 차감된다.")
        @Test
        fun decreasesStockInAscendingProductIdOrder() {
            // arrange
            // user() 를 whenever(...).thenReturn(user()) 처럼 인자 자리에서 바로 부르면 안 된다. (user() KDoc 참고)
            val loggedInUser = user()
            whenever(userService.getUser(LOGIN_ID)).thenReturn(loggedInUser)
            whenever(productService.getProductsByIds(any()))
                .thenReturn(listOf(product(1L), product(2L), product(3L)))
            whenever(productService.decreaseStock(any(), any())).thenReturn(true)
            whenever(orderService.place(any(), any(), any(), anyOrNull()))
                .thenReturn(order(items = listOf(orderItem(3L), orderItem(1L), orderItem(2L))))

            val command = OrderCommand.Place(
                loginId = LOGIN_ID,
                items = listOf(
                    OrderCommand.Item(productId = 3L, quantity = Quantity(1)),
                    OrderCommand.Item(productId = 1L, quantity = Quantity(1)),
                    OrderCommand.Item(productId = 2L, quantity = Quantity(1)),
                ),
            )

            // act
            orderFacade.place(command)

            // assert
            // OrderFacade.kt:43-45 의 데드락 방지 계약 — 모든 트랜잭션이 같은 순서로 락을 잡아야 서로를
            // 기다리는 고리가 생기지 않는다. 통합 테스트로는 실제 락 경합 없이 차감 순서를 관찰할 수 없어,
            // 이 단위 테스트가 사실상 유일하게 이 계약을 고정한다. 아래 "요청 순서 그대로 저장" 케이스와 짝이다 —
            // 정렬한 것은 차감 순서뿐이라는 계약의 반쪽씩을 나눠 검증한다.
            val orderedProductService = inOrder(productService)
            orderedProductService.verify(productService).decreaseStock(productId = eq(1L), quantity = any())
            orderedProductService.verify(productService).decreaseStock(productId = eq(2L), quantity = any())
            orderedProductService.verify(productService).decreaseStock(productId = eq(3L), quantity = any())
        }

        @DisplayName("저장되는 항목의 순서는 정렬 전 요청 순서 그대로다.")
        @Test
        fun savesItemsInOriginalRequestOrder() {
            // arrange
            // user() 를 whenever(...).thenReturn(user()) 처럼 인자 자리에서 바로 부르면 안 된다. (user() KDoc 참고)
            val loggedInUser = user()
            whenever(userService.getUser(LOGIN_ID)).thenReturn(loggedInUser)
            whenever(productService.getProductsByIds(any()))
                .thenReturn(listOf(product(1L), product(2L), product(3L)))
            whenever(productService.decreaseStock(any(), any())).thenReturn(true)
            whenever(orderService.place(any(), any(), any(), anyOrNull()))
                .thenReturn(order(items = listOf(orderItem(3L), orderItem(1L), orderItem(2L))))

            val command = OrderCommand.Place(
                loginId = LOGIN_ID,
                items = listOf(
                    OrderCommand.Item(productId = 3L, quantity = Quantity(1)),
                    OrderCommand.Item(productId = 1L, quantity = Quantity(1)),
                    OrderCommand.Item(productId = 2L, quantity = Quantity(1)),
                ),
            )
            val captor = argumentCaptor<List<OrderItemModel>>()

            // act
            orderFacade.place(command)

            // assert
            // OrderFacade.kt:44 의 "정렬한 것은 차감 순서뿐이다" 계약. 위 차감 순서 케이스와 짝을 이뤄
            // 정렬이 차감에만 쓰이고 저장 순서에는 영향을 주지 않음을 함께 고정한다.
            verify(orderService).place(userId = any(), items = captor.capture(), discountAmount = any(), usedCouponId = anyOrNull())
            assertThat(captor.firstValue.map { it.productId }).containsExactly(3L, 1L, 2L)
        }

        @DisplayName("항목마다 요청한 수량만큼 재고를 차감한다.")
        @Test
        fun decreasesStockByRequestedQuantityPerItem() {
            // arrange
            // user() 를 whenever(...).thenReturn(user()) 처럼 인자 자리에서 바로 부르면 안 된다. (user() KDoc 참고)
            val loggedInUser = user()
            whenever(userService.getUser(LOGIN_ID)).thenReturn(loggedInUser)
            whenever(productService.getProductsByIds(any())).thenReturn(listOf(product(1L), product(2L)))
            whenever(productService.decreaseStock(any(), any())).thenReturn(true)
            whenever(orderService.place(any(), any(), any(), anyOrNull()))
                .thenReturn(order(items = listOf(orderItem(1L, 2), orderItem(2L, 5))))

            val command = OrderCommand.Place(
                loginId = LOGIN_ID,
                items = listOf(
                    OrderCommand.Item(productId = 1L, quantity = Quantity(2)),
                    OrderCommand.Item(productId = 2L, quantity = Quantity(5)),
                ),
            )

            // act
            orderFacade.place(command)

            // assert
            assertAll(
                { verify(productService).decreaseStock(productId = eq(1L), quantity = eq(2)) },
                { verify(productService).decreaseStock(productId = eq(2L), quantity = eq(5)) },
            )
        }

        @DisplayName("저장 항목은 조회한 상품의 이름과 단가를 스냅샷으로 담는다.")
        @Test
        fun savesProductNameAndPriceSnapshot() {
            // arrange
            // user() 를 whenever(...).thenReturn(user()) 처럼 인자 자리에서 바로 부르면 안 된다. (user() KDoc 참고)
            val loggedInUser = user()
            whenever(userService.getUser(LOGIN_ID)).thenReturn(loggedInUser)
            val snapshotProduct = product(id = 1L, name = "운동화", price = 39_000L)
            whenever(productService.getProductsByIds(any())).thenReturn(listOf(snapshotProduct))
            whenever(productService.decreaseStock(any(), any())).thenReturn(true)
            whenever(orderService.place(any(), any(), any(), anyOrNull())).thenReturn(order(items = listOf(orderItem(1L, 2))))

            val command = OrderCommand.Place(
                loginId = LOGIN_ID,
                items = listOf(OrderCommand.Item(productId = 1L, quantity = Quantity(2))),
            )
            val captor = argumentCaptor<List<OrderItemModel>>()

            // act
            orderFacade.place(command)

            // assert
            verify(orderService).place(userId = any(), items = captor.capture(), discountAmount = any(), usedCouponId = anyOrNull())
            val savedItem = captor.firstValue.first()
            assertAll(
                { assertThat(savedItem.productName).isEqualTo(snapshotProduct.name) },
                { assertThat(savedItem.unitPrice).isEqualTo(snapshotProduct.price) },
            )
        }

        @DisplayName("조회한 회원의 ID 를 orderService.place 에 전달한다.")
        @Test
        fun passesQueriedUserIdToOrderService() {
            // arrange
            val queriedUser = user(id = 42L)
            whenever(userService.getUser(LOGIN_ID)).thenReturn(queriedUser)
            whenever(productService.getProductsByIds(any())).thenReturn(listOf(product(1L)))
            whenever(productService.decreaseStock(any(), any())).thenReturn(true)
            whenever(orderService.place(any(), any(), any(), anyOrNull())).thenReturn(order(userId = 42L))

            val command = OrderCommand.Place(
                loginId = LOGIN_ID,
                items = listOf(OrderCommand.Item(productId = 1L, quantity = Quantity(1))),
            )

            // act
            orderFacade.place(command)

            // assert
            verify(orderService).place(userId = eq(42L), items = any(), discountAmount = any(), usedCouponId = anyOrNull())
        }
    }

    @DisplayName("주문할 때, ")
    @Nested
    inner class PlaceFailure {
        @DisplayName("재고가 모자라면 CONFLICT 예외를 던진다.")
        @Test
        fun throwsConflict_whenStockIsInsufficient() {
            // arrange
            // user() 를 whenever(...).thenReturn(user()) 처럼 인자 자리에서 바로 부르면 안 된다. (user() KDoc 참고)
            val loggedInUser = user()
            whenever(userService.getUser(LOGIN_ID)).thenReturn(loggedInUser)
            whenever(productService.getProductsByIds(any())).thenReturn(listOf(product(1L)))
            whenever(productService.decreaseStock(any(), any())).thenReturn(false)

            val command = OrderCommand.Place(
                loginId = LOGIN_ID,
                items = listOf(OrderCommand.Item(productId = 1L, quantity = Quantity(1))),
            )

            // act
            val result = assertThrows<CoreException> { orderFacade.place(command) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("재고가 모자라면 주문을 저장하지 않는다.")
        @Test
        fun doesNotPlaceOrder_whenStockIsInsufficient() {
            // arrange
            // user() 를 whenever(...).thenReturn(user()) 처럼 인자 자리에서 바로 부르면 안 된다. (user() KDoc 참고)
            val loggedInUser = user()
            whenever(userService.getUser(LOGIN_ID)).thenReturn(loggedInUser)
            whenever(productService.getProductsByIds(any())).thenReturn(listOf(product(1L)))
            whenever(productService.decreaseStock(any(), any())).thenReturn(false)

            val command = OrderCommand.Place(
                loginId = LOGIN_ID,
                items = listOf(OrderCommand.Item(productId = 1L, quantity = Quantity(1))),
            )

            // act
            assertThrows<CoreException> { orderFacade.place(command) }

            // assert
            verify(orderService, never()).place(any(), any(), any(), anyOrNull())
        }

        @DisplayName("앞 항목의 차감이 실패하면 뒤 항목은 차감하지 않는다.")
        @Test
        fun stopsDecreasingStock_whenEarlierItemFails() {
            // arrange
            // user() 를 whenever(...).thenReturn(user()) 처럼 인자 자리에서 바로 부르면 안 된다. (user() KDoc 참고)
            val loggedInUser = user()
            whenever(userService.getUser(LOGIN_ID)).thenReturn(loggedInUser)
            whenever(productService.getProductsByIds(any())).thenReturn(listOf(product(1L), product(2L)))
            whenever(productService.decreaseStock(productId = eq(1L), quantity = any())).thenReturn(false)

            val command = OrderCommand.Place(
                loginId = LOGIN_ID,
                items = listOf(
                    OrderCommand.Item(productId = 1L, quantity = Quantity(1)),
                    OrderCommand.Item(productId = 2L, quantity = Quantity(1)),
                ),
            )

            // act
            // place() 의 forEach 는 첫 항목에서 CoreException 이 던져지는 순간 중단된다. 뒤 항목은 아예 순회되지 않는다.
            assertThrows<CoreException> { orderFacade.place(command) }

            // assert
            verify(productService, never()).decreaseStock(productId = eq(2L), quantity = any())
        }

        @DisplayName("가입되지 않은 로그인 ID 면 NOT_FOUND 예외를 던지고 재고를 건드리지 않는다.")
        @Test
        fun throwsNotFound_whenUserDoesNotExist() {
            // arrange
            whenever(userService.getUser(LOGIN_ID)).thenReturn(null)

            val command = OrderCommand.Place(
                loginId = LOGIN_ID,
                items = listOf(OrderCommand.Item(productId = 1L, quantity = Quantity(1))),
            )

            // act
            val result = assertThrows<CoreException> { orderFacade.place(command) }

            // assert
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND) },
                { verify(productService, never()).decreaseStock(any(), any()) },
            )
        }

        @DisplayName("요청한 상품 중 없는 것이 있으면 NOT_FOUND 예외를 던지고 재고를 건드리지 않는다.")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            // arrange
            // user() 를 whenever(...).thenReturn(user()) 처럼 인자 자리에서 바로 부르면 안 된다. (user() KDoc 참고)
            val loggedInUser = user()
            whenever(userService.getUser(LOGIN_ID)).thenReturn(loggedInUser)
            // 상품 2개를 요청했지만 1개만 조회되어 "없는 상품이 섞였다" 를 재현한다.
            whenever(productService.getProductsByIds(any())).thenReturn(listOf(product(1L)))

            val command = OrderCommand.Place(
                loginId = LOGIN_ID,
                items = listOf(
                    OrderCommand.Item(productId = 1L, quantity = Quantity(1)),
                    OrderCommand.Item(productId = 2L, quantity = Quantity(1)),
                ),
            )

            // act
            val result = assertThrows<CoreException> { orderFacade.place(command) }

            // assert
            // loadProductsOrThrow 의 존재 검증이 decreaseStock 차감 루프보다 먼저 실행된다는 순서 계약이 핵심이다.
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND) },
                { verify(productService, never()).decreaseStock(any(), any()) },
            )
        }
    }

    @DisplayName("주문을 단건 조회할 때, ")
    @Nested
    inner class GetOrder {
        @DisplayName("남의 주문이면 NOT_FOUND 예외를 던진다.")
        @Test
        fun throwsNotFound_whenOrderBelongsToAnotherUser() {
            // arrange
            // OrderFacade.kt:74-77 의 보안 판단 — 403 이 아니라 404 인 것은 주문 존재 자체를 숨기기 위해서다.
            // 403 은 "그 주문은 존재한다" 를 알려주므로 ID 를 훑으면 주문량과 증가 속도가 드러난다.
            // user() 를 whenever(...).thenReturn(user()) 처럼 인자 자리에서 바로 부르면 안 된다. (user() KDoc 참고)
            val loggedInUser = user(id = USER_ID)
            whenever(userService.getUser(LOGIN_ID)).thenReturn(loggedInUser)
            val othersOrder = OrderModel.create(userId = 999L, items = listOf(orderItem(1L)))
            whenever(orderService.getOrder(1L)).thenReturn(othersOrder)

            // act
            val result = assertThrows<CoreException> { orderFacade.getOrder(LOGIN_ID, 1L) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("없는 주문이면 NOT_FOUND 예외를 던진다.")
        @Test
        fun throwsNotFound_whenOrderDoesNotExist() {
            // arrange
            // user() 를 whenever(...).thenReturn(user()) 처럼 인자 자리에서 바로 부르면 안 된다. (user() KDoc 참고)
            val loggedInUser = user()
            whenever(userService.getUser(LOGIN_ID)).thenReturn(loggedInUser)
            whenever(orderService.getOrder(1L)).thenReturn(null)

            // act
            val result = assertThrows<CoreException> { orderFacade.getOrder(LOGIN_ID, 1L) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("쿠폰을 적용해 주문할 때, ")
    @Nested
    inner class PlaceWithCoupon {
        private val userCouponId = 7L

        private fun availableCoupon(discountValue: Long = 5_000L): UserCouponModel =
            UserCouponModel.issue(
                userId = USER_ID,
                coupon = CouponModel.create(
                    name = CouponName("테스트 쿠폰"),
                    discountType = DiscountType.FIXED,
                    discountValue = discountValue,
                    expiresAt = ZonedDateTime.now().plusDays(30),
                ).withId(10L),
            ).withId(userCouponId)

        private fun singleItemCommand() = OrderCommand.Place(
            loginId = LOGIN_ID,
            items = listOf(OrderCommand.Item(productId = 1L, quantity = Quantity(2))),
            userCouponId = userCouponId,
        )

        @DisplayName("쿠폰을 재고보다 먼저 소모한다.")
        @Test
        fun usesCouponBeforeDecreasingStock() {
            // arrange
            // 설계 문서 6.4 장의 잠금 순서 계약이다. 경합이 적은 user_coupons 락을 먼저 잡고
            // 경합이 심한 products 락을 나중에 잡아야 심한 쪽의 보유 시간이 짧아진다.
            // 통합 테스트로는 실제 락 경합 없이 이 순서를 관찰할 수 없다.
            val loggedInUser = user()
            whenever(userService.getUser(LOGIN_ID)).thenReturn(loggedInUser)
            whenever(productService.getProductsByIds(any())).thenReturn(listOf(product(1L)))
            whenever(couponService.getUserCoupon(eq(userCouponId), eq(USER_ID))).thenReturn(availableCoupon())
            whenever(couponService.use(eq(userCouponId), eq(USER_ID))).thenReturn(true)
            whenever(productService.decreaseStock(any(), any())).thenReturn(true)
            whenever(orderService.place(any(), any(), any(), anyOrNull())).thenReturn(order())

            // act
            orderFacade.place(singleItemCommand())

            // assert
            val ordered = inOrder(couponService, productService)
            ordered.verify(couponService).use(eq(userCouponId), eq(USER_ID))
            ordered.verify(productService).decreaseStock(productId = any(), quantity = any())
        }

        @DisplayName("할인 금액이 주문 저장에 그대로 전달된다.")
        @Test
        fun passesDiscountToOrderService() {
            // arrange
            // 상품 단가 1,000 원 × 2 개 = 2,000 원. 정액 5,000 원 쿠폰이므로 총액까지만 깎여 2,000 원이다.
            val loggedInUser = user()
            whenever(userService.getUser(LOGIN_ID)).thenReturn(loggedInUser)
            whenever(productService.getProductsByIds(any())).thenReturn(listOf(product(1L)))
            whenever(couponService.getUserCoupon(eq(userCouponId), eq(USER_ID))).thenReturn(availableCoupon())
            whenever(couponService.use(eq(userCouponId), eq(USER_ID))).thenReturn(true)
            whenever(productService.decreaseStock(any(), any())).thenReturn(true)
            whenever(orderService.place(any(), any(), any(), anyOrNull())).thenReturn(order())

            // act
            orderFacade.place(singleItemCommand())

            // assert
            verify(orderService).place(
                userId = eq(USER_ID),
                items = any(),
                discountAmount = eq(Price(2_000)),
                usedCouponId = eq(userCouponId),
            )
        }

        @DisplayName("쿠폰을 지정하지 않으면, 쿠폰 서비스를 호출하지 않는다.")
        @Test
        fun doesNotTouchCouponService_whenNotSpecified() {
            // arrange
            val loggedInUser = user()
            whenever(userService.getUser(LOGIN_ID)).thenReturn(loggedInUser)
            whenever(productService.getProductsByIds(any())).thenReturn(listOf(product(1L)))
            whenever(productService.decreaseStock(any(), any())).thenReturn(true)
            whenever(orderService.place(any(), any(), any(), anyOrNull())).thenReturn(order())

            val command = OrderCommand.Place(
                loginId = LOGIN_ID,
                items = listOf(OrderCommand.Item(productId = 1L, quantity = Quantity(1))),
            )

            // act
            orderFacade.place(command)

            // assert
            assertAll(
                { verify(couponService, never()).getUserCoupon(any(), any()) },
                { verify(couponService, never()).use(any(), any()) },
            )
        }

        @DisplayName("쿠폰 조회가 비면, use 를 호출하지 않고 NOT_FOUND 를 던진다.")
        @Test
        fun throwsNotFound_whenCouponMissing() {
            // arrange
            // 설계 문서 6.3 장의 2 단계 구조 — 조회가 404 를, 조건부 UPDATE 가 409 를 판정한다.
            val loggedInUser = user()
            whenever(userService.getUser(LOGIN_ID)).thenReturn(loggedInUser)
            whenever(productService.getProductsByIds(any())).thenReturn(listOf(product(1L)))
            whenever(couponService.getUserCoupon(eq(userCouponId), eq(USER_ID))).thenReturn(null)

            // act
            val result = assertThrows<CoreException> { orderFacade.place(singleItemCommand()) }

            // assert
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND) },
                { verify(couponService, never()).use(any(), any()) },
                { verify(productService, never()).decreaseStock(any(), any()) },
            )
        }

        @DisplayName("쿠폰 소모가 실패하면, CONFLICT 를 던지고 재고를 건드리지 않는다.")
        @Test
        fun throwsConflict_whenCouponAlreadyUsedOrExpired() {
            // arrange
            val loggedInUser = user()
            whenever(userService.getUser(LOGIN_ID)).thenReturn(loggedInUser)
            whenever(productService.getProductsByIds(any())).thenReturn(listOf(product(1L)))
            whenever(couponService.getUserCoupon(eq(userCouponId), eq(USER_ID))).thenReturn(availableCoupon())
            whenever(couponService.use(eq(userCouponId), eq(USER_ID))).thenReturn(false)

            // act
            val result = assertThrows<CoreException> { orderFacade.place(singleItemCommand()) }

            // assert
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT) },
                { verify(productService, never()).decreaseStock(any(), any()) },
                { verify(orderService, never()).place(any(), any(), any(), anyOrNull()) },
            )
        }
    }
}
