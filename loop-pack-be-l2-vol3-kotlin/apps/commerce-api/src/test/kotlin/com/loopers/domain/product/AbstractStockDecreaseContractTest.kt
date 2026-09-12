package com.loopers.domain.product

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.utils.DatabaseCleanUp
import kotlin.reflect.KClass
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate

/**
 * 세 락 전략이 공통으로 지켜야 하는 계약. (2026-09-09 설계 문서 6.4 장)
 *
 * 구체 클래스마다 @SpringBootTest(properties=[...]) 로 스프링 컨텍스트를 따로 띄운다 — 컨텍스트마다
 * 다른 StockDecreaseStrategy 빈 하나만 올라오므로, 같은 테스트 본문이 세 구현을 각각 검증하게 된다.
 *
 * updated_at 불변은 여기서 공통으로 확인하지 않는다. 낙관적 락은 엔티티 dirty checking 을 거쳐야
 * @Version 검사가 걸리므로 BaseEntity.preUpdate 가 함께 돈다 — 세 전략 중 유일하게 updated_at 이
 * 갱신된다. 이것은 구현의 실수가 아니라 그 기법 자체의 성질이다(태스크 2 배경 참고). 그래서
 * 조건부 UPDATE·비관적 락 서브클래스에만 그 전용 테스트를 따로 둔다.
 */
abstract class AbstractStockDecreaseContractTest {
    @Autowired
    protected lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var productService: ProductService

    @Autowired
    private lateinit var brandRepository: BrandRepository

    @Autowired
    private lateinit var stockDecreaseStrategy: StockDecreaseStrategy

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    private lateinit var databaseCleanUp: DatabaseCleanUp

    /** 서브클래스가 자기 properties 로 올라와야 하는 전략을 선언한다. */
    protected abstract val expectedStrategy: KClass<out StockDecreaseStrategy>

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    /**
     * 차감을 트랜잭션 안에서 부른다. ProductRepositoryImpl 에는 Transactional 이 없고 프로덕션에서는
     * ProductService.decreaseStock 이 경계를 만든다 — 테스트가 저장소를 직접 부르면 그 경계가 없다.
     * 낙관적 락의 entityManager.flush() 와 비관적 락의 PESSIMISTIC_WRITE 는 활성 트랜잭션을 요구하므로,
     * 감싸지 않으면 세 전략이 같은 조건에서 비교되지 않는다.
     */
    protected fun decreaseStock(productId: Long, quantity: Int): Int =
        transactionTemplate.execute { productRepository.decreaseStock(productId, quantity) }!!

    /**
     * 이 계약 테스트가 정말 의도한 전략 위에서 도는지 확인한다.
     * properties 문자열에 오타가 나면 matchIfMissing = true 인 조건부 UPDATE 가 조용히 올라와,
     * 세 클래스가 같은 전략을 세 번 검증하면서 "세 전략이 같은 계약을 만족한다" 는 결론을 낸다.
     */
    @DisplayName("이 계약 테스트는 의도한 전략 위에서 돈다.")
    @Test
    fun runsOnExpectedStrategy() {
        assertThat(stockDecreaseStrategy).isInstanceOf(expectedStrategy.java)
    }

    protected fun saveProduct(stock: Long): ProductModel {
        val brand = brandRepository.save(BrandModel.create(BrandName("루퍼스")))
        return productRepository.save(
            ProductModel.create(
                brandId = brand.id,
                name = ProductName("상품"),
                price = Price(1_000),
                stock = Stock(stock),
            ),
        )
    }

    @DisplayName("재고가 넉넉하면, 정확히 요청 수량만큼 줄고 영향 행 수는 1 이다.")
    @Test
    fun decreasesByQuantity_whenStockIsSufficient() {
        // arrange
        val product = saveProduct(stock = 10)

        // act
        val affected = decreaseStock(product.id, 3)

        // assert
        assertAll(
            { assertThat(affected).isEqualTo(1) },
            { assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(7L) },
        )
    }

    @DisplayName("재고와 요청 수량이 같으면, 0 이 되고 영향 행 수는 1 이다.")
    @Test
    fun decreasesToZero_whenStockEqualsQuantity() {
        // arrange
        val product = saveProduct(stock = 5)

        // act
        val affected = decreaseStock(product.id, 5)

        // assert
        assertAll(
            { assertThat(affected).isEqualTo(1) },
            { assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(0L) },
        )
    }

    /** 초과 판매 방지의 본체다. 이 단언이 실패하면 그 전략의 WHERE 절(또는 낙관적 락의 사전 검사)이 깨진 것이다. */
    @DisplayName("재고가 모자라면, 아무것도 바꾸지 않고 영향 행 수는 0 이다.")
    @Test
    fun doesNothing_whenStockIsInsufficient() {
        // arrange
        val product = saveProduct(stock = 2)

        // act
        val affected = decreaseStock(product.id, 3)

        // assert
        assertAll(
            { assertThat(affected).isEqualTo(0) },
            { assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(2L) },
        )
    }

    @DisplayName("삭제된 상품이면, 영향 행 수는 0 이다.")
    @Test
    fun returnsZero_whenProductIsSoftDeleted() {
        // arrange
        val product = saveProduct(stock = 10)
        productService.delete(product.id)

        // act
        val affected = decreaseStock(product.id, 1)

        // assert
        assertThat(affected).isEqualTo(0)
    }

    @DisplayName("존재하지 않는 상품이면, 영향 행 수는 0 이다.")
    @Test
    fun returnsZero_whenProductDoesNotExist() {
        // act
        val affected = decreaseStock(999_999L, 1)

        // assert
        assertThat(affected).isEqualTo(0)
    }
}
