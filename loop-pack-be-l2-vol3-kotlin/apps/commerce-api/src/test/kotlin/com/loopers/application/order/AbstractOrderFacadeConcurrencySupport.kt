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
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockDecreaseStrategy
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserName
import com.loopers.domain.user.UserService
import com.loopers.utils.DatabaseCleanUp
import kotlin.reflect.KClass
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 동시성 테스트가 공유하는 주입과 헬퍼. 단언은 여기 없다.
 *
 * 조건부 UPDATE 와 비관적 락은 경합하면 행 락에서 기다렸다가 자기 차례에 성공하지만, 낙관적 락은
 * 기다린 뒤 실패한다 — 재시도 상한을 넘기면 CONFLICT 다. (2026-09-09 설계 문서 3.2 장)
 * 성사 건수에 대한 단언이 전략마다 다른 이유이며, 그래서 단언은 아래 두 갈래가 나눠 갖는다.
 */
abstract class AbstractOrderFacadeConcurrencySupport {
    @Autowired
    protected lateinit var orderFacade: OrderFacade

    @Autowired
    protected lateinit var userService: UserService

    @Autowired
    protected lateinit var brandRepository: BrandRepository

    @Autowired
    protected lateinit var productRepository: ProductRepository

    @Autowired
    protected lateinit var databaseCleanUp: DatabaseCleanUp

    @Autowired
    protected lateinit var stockDecreaseStrategy: StockDecreaseStrategy

    /** 서브클래스가 자기 properties 로 올라와야 하는 전략을 선언한다. 계약 테스트와 같은 이유다. */
    protected abstract val expectedStrategy: KClass<out StockDecreaseStrategy>

    @DisplayName("이 동시성 테스트는 의도한 전략 위에서 돈다.")
    @Test
    fun runsOnExpectedStrategy() {
        assertThat(stockDecreaseStrategy).isInstanceOf(expectedStrategy.java)
    }

    companion object {
        const val CONCURRENT_USERS = 10
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    protected fun signUp(loginId: String): UserModel =
        userService.signUp(
            UserCommand.SignUp(
                loginId = LoginId(loginId),
                password = RawPassword("Loopers1!"),
                name = UserName("홍길동"),
                birthDate = BirthDate.from("1990-01-01"),
                email = Email("$loginId@loopers.com"),
            ),
        )

    protected fun saveProduct(name: String = "상품", stock: Long): ProductModel {
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

    protected fun stockOf(productId: Long): Long = productRepository.findById(productId)!!.stock.value

    protected fun place(loginId: LoginId, vararg items: Pair<Long, Int>) =
        orderFacade.place(
            OrderCommand.Place(
                loginId = loginId,
                items = items.map { OrderCommand.Item(productId = it.first, quantity = Quantity(it.second)) },
            ),
        )

    /**
     * 모든 스레드를 같은 순간에 출발시킨다.
     * 순차 실행이면 경합이 재현되지 않아 테스트가 있으나 마나가 되므로 시작 래치가 필요하다.
     *
     * 실패를 삼키지 않고 모아서 돌려준다. 어떤 테스트는 실패가 0 이어야 하고
     * 어떤 테스트는 정확히 몇 건이어야 하므로, 판정은 호출자가 한다.
     */
    protected fun runConcurrently(count: Int, task: (Int) -> Unit): List<Throwable> {
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
}
