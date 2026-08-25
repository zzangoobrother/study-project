package com.loopers.application.order

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.Quantity
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
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserName
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
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

    private fun place(loginId: LoginId, vararg items: Pair<Long, Int>) =
        orderFacade.place(
            OrderCommand.Place(
                loginId = loginId,
                items = items.map { OrderCommand.Item(productId = it.first, quantity = Quantity(it.second)) },
            ),
        )

    private fun stockOf(productId: Long): Long = productRepository.findById(productId)!!.stock.value

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
        @Disabled("Task 8 에서 getOrder 가 생기면 활성화한다")
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
}
