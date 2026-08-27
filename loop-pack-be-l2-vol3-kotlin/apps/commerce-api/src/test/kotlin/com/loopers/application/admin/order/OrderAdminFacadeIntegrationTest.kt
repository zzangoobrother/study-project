package com.loopers.application.admin.order

import com.loopers.domain.order.OrderCriteria
import com.loopers.domain.order.OrderItemModel
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.Quantity
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductName
import com.loopers.domain.support.PageQuery
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserName
import com.loopers.domain.user.UserRepository
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean

@SpringBootTest
class OrderAdminFacadeIntegrationTest @Autowired constructor(
    private val orderAdminFacade: OrderAdminFacade,
    private val orderService: OrderService,
    private val userRepository: UserRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockitoSpyBean
    private lateinit var userService: UserService

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

    private fun item(productId: Long = 1L, name: String = "운동화", price: Long = 10_000, quantity: Int = 1) =
        OrderItemModel.create(
            productId = productId,
            productName = ProductName(name),
            unitPrice = Price(price),
            quantity = Quantity(quantity),
        )

    private fun place(userId: Long, vararg items: OrderItemModel) =
        orderService.place(userId = userId, items = items.toList())

    private fun adminSearch(page: Int = 0, size: Int = 20) =
        OrderCriteria.AdminSearch(pageQuery = PageQuery(page = page, size = size))

    @DisplayName("어드민이 주문 목록을 조회할 때, ")
    @Nested
    inner class GetOrders {
        @DisplayName("여러 회원의 주문이 모두 나오고, 각각 loginId 가 채워진다.")
        @Test
        fun fillsLoginId_forEachUsersOrder() {
            // arrange
            val first = signUp("loopers01")
            val second = signUp("loopers02")
            place(first.id, item(productId = 1))
            place(second.id, item(productId = 2))

            // act
            val result = orderAdminFacade.getOrders(adminSearch())

            // assert
            assertAll(
                { assertThat(result.content).hasSize(2) },
                { assertThat(result.content.map { it.user?.loginId }).containsExactlyInAnyOrder("loopers01", "loopers02") },
            )
        }

        /**
         * 탈퇴 회원을 결과에서 빼면 "탈퇴한 회원의 주문" 과 "알 수 없는 회원의 주문" 이 둘 다
         * user = null 로 뭉개진다. findAllByIdsIncludingDeleted 를 만든 이유가 바로 이 테스트다.
         */
        @DisplayName("탈퇴한 회원의 주문도 loginId 가 채워진다.")
        @Test
        fun fillsLoginId_evenWhenUserIsSoftDeleted() {
            // arrange
            val withdrawn = signUp("loopers01")
            val placed = place(withdrawn.id, item(productId = 1))
            withdrawn.delete()
            userRepository.save(withdrawn)

            // act
            val result = orderAdminFacade.getOrders(adminSearch())

            // assert
            val found = result.content.single { it.id == placed.id }
            assertAll(
                { assertThat(found.user?.id).isEqualTo(withdrawn.id) },
                { assertThat(found.user?.loginId).isEqualTo("loopers01") },
            )
        }

        @DisplayName("items 가 빈 배열이다 (요약 응답).")
        @Test
        fun returnsEmptyItems() {
            // arrange
            val user = signUp()
            place(user.id, item(productId = 1), item(productId = 2))

            // act
            val result = orderAdminFacade.getOrders(adminSearch())

            // assert
            assertThat(result.content.first().items).isEmpty()
        }

        @DisplayName("최근 주문이 앞에 오고, 페이징 메타가 전체 회원의 주문 개수를 기준으로 채워진다.")
        @Test
        fun ordersByMostRecent_withPagingMetadata() {
            // arrange
            val user = signUp()
            val first = place(user.id, item(productId = 1))
            val second = place(user.id, item(productId = 2))
            val third = place(user.id, item(productId = 3))

            // act
            val result = orderAdminFacade.getOrders(adminSearch(page = 0, size = 2))

            // assert
            assertAll(
                { assertThat(result.content.map { it.id }).containsExactly(third.id, second.id) },
                { assertThat(result.page).isEqualTo(0) },
                { assertThat(result.size).isEqualTo(2) },
                { assertThat(result.totalElements).isEqualTo(3L) },
                { assertThat(result.totalPages).isEqualTo(2) },
            )
        }

        /**
         * ProductFacadeIntegrationTest.queriesBrandsOnlyOnce_regardlessOfProductCount 와 같은 취지의 방어선이다.
         * loadUsers 가 userId 를 distinct() 하지 않으면 이 검증이 깨진다.
         */
        @DisplayName("여러 주문이 같은 회원의 것이어도, 회원 조회는 1회만 수행된다.")
        @Test
        fun queriesUsersOnlyOnce_regardlessOfOrderCount() {
            // arrange
            val user = signUp()
            repeat(5) { index -> place(user.id, item(productId = (index + 1).toLong())) }

            // act
            orderAdminFacade.getOrders(adminSearch())

            // assert
            verify(userService, times(1)).getUsersIncludingDeleted(listOf(user.id))
        }
    }

    @DisplayName("어드민이 주문을 단건 조회할 때, ")
    @Nested
    inner class GetOrder {
        @DisplayName("항목이 채워지고 총액과 항목 수가 맞다.")
        @Test
        fun fillsItemsAndTotals() {
            // arrange
            val user = signUp()
            val placed = place(
                user.id,
                item(productId = 1, name = "운동화", price = 10_000, quantity = 2),
                item(productId = 2, name = "양말", price = 3_000, quantity = 1),
            )

            // act
            val found = orderAdminFacade.getOrder(placed.id)

            // assert
            assertAll(
                { assertThat(found.items).hasSize(2) },
                { assertThat(found.totalPrice).isEqualTo(23_000L) },
                { assertThat(found.itemCount).isEqualTo(2) },
                { assertThat(found.user?.id).isEqualTo(user.id) },
                { assertThat(found.user?.loginId).isEqualTo(user.loginId.value) },
            )
        }

        /**
         * 소유자 검증이 없다는 것이 이 테스트의 핵심이다.
         * 공개 OrderFacade.getOrder 는 남의 주문이면 NOT_FOUND 로 숨기지만, 이 메서드에는
         * "누구의 주문인지" 를 가릴 기준이 되는 호출자 정보 자체가 없다 — 어떤 회원의 주문이든 조회된다.
         */
        @DisplayName("다른 회원의 주문도 예외 없이 조회된다.")
        @Test
        fun returnsOrder_regardlessOfWhichUserPlacedIt() {
            // arrange
            val owner = signUp("loopers01")
            val other = signUp("loopers02")
            val ownerOrder = place(owner.id, item(productId = 1))
            val otherOrder = place(other.id, item(productId = 2))

            // act
            val foundOwnerOrder = orderAdminFacade.getOrder(ownerOrder.id)
            val foundOtherOrder = orderAdminFacade.getOrder(otherOrder.id)

            // assert
            assertAll(
                { assertThat(foundOwnerOrder.user?.id).isEqualTo(owner.id) },
                { assertThat(foundOtherOrder.user?.id).isEqualTo(other.id) },
            )
        }

        @DisplayName("존재하지 않는 주문이면, NOT_FOUND 를 던진다.")
        @Test
        fun throwsNotFound_whenOrderDoesNotExist() {
            // act & assert
            assertThatThrownBy { orderAdminFacade.getOrder(99999L) }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
