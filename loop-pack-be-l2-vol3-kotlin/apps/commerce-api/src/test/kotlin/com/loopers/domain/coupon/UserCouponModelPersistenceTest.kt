package com.loopers.domain.coupon

import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.utils.DatabaseCleanUp
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

/**
 * 정책 저장에 CouponJpaRepository 를 직접 쓰는 이유는 도메인 CouponRepository 에 save 가 없기 때문이다.
 * 정책은 시더만 만들고 애플리케이션은 읽기만 하므로 도메인 계약을 넓히지 않는다.
 */
@SpringBootTest
class UserCouponModelPersistenceTest @Autowired constructor(
    private val userCouponRepository: UserCouponRepository,
    private val couponJpaRepository: CouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun savedCoupon(expiresAt: ZonedDateTime = ZonedDateTime.now().plusDays(30)): CouponModel =
        couponJpaRepository.save(
            CouponModel.create(
                name = CouponName("테스트 쿠폰"),
                discountType = DiscountType.RATE,
                discountValue = 10,
                expiresAt = expiresAt,
            ),
        )

    @DisplayName("발급 쿠폰을 저장하면, ")
    @Nested
    inner class Persist {
        @DisplayName("스냅샷 값이 컬럼으로 저장되고 다시 읽을 때 복원된다.")
        @Test
        fun persistsAndRestoresSnapshot() {
            // arrange
            val expiresAt = ZonedDateTime.now().plusDays(30)
            val coupon = savedCoupon(expiresAt)

            // act
            val saved = userCouponRepository.save(UserCouponModel.issue(userId = 1L, coupon = coupon))
            val found = userCouponRepository.findByIdAndUserId(id = saved.id, userId = 1L)

            // assert
            assertAll(
                { assertThat(found).isNotNull },
                { assertThat(found?.couponId).isEqualTo(coupon.id) },
                { assertThat(found?.discountType).isEqualTo(DiscountType.RATE) },
                { assertThat(found?.discountValue).isEqualTo(10) },
                { assertThat(found?.usedAt).isNull() },
            )
        }

        @DisplayName("같은 회원과 정책 조합이 이미 있으면, 유니크 제약에 걸린다.")
        @Test
        fun violatesUniqueConstraint_whenDuplicated() {
            // arrange
            val coupon = savedCoupon()
            userCouponRepository.save(UserCouponModel.issue(userId = 1L, coupon = coupon))

            // act & assert
            assertThatThrownBy {
                userCouponRepository.save(UserCouponModel.issue(userId = 1L, coupon = coupon))
            }.isInstanceOf(DataIntegrityViolationException::class.java)
        }

        @DisplayName("다른 회원이면 같은 정책을 발급받을 수 있다.")
        @Test
        fun allowsSameCouponForDifferentUsers() {
            // arrange
            val coupon = savedCoupon()
            userCouponRepository.save(UserCouponModel.issue(userId = 1L, coupon = coupon))

            // act
            val second = userCouponRepository.save(UserCouponModel.issue(userId = 2L, coupon = coupon))

            // assert
            assertThat(second.id).isPositive()
        }
    }

    /**
     * use 는 JPQL 벌크 UPDATE(@Modifying) 라 실행 시점에 활성 트랜잭션이 있어야 한다.
     * 트랜잭션 경계는 원래 이 저장소를 호출하는 서비스/파사드가 쥐지만, 이 태스크는 저장소만 만들 뿐
     * 그 상위 계층이 아직 없다. 그래서 여기서는 테스트 메서드가 그 경계를 대신 연다.
     */
    @DisplayName("쿠폰을 소모할 때, ")
    @Nested
    inner class Use {
        @DisplayName("미사용이고 만료 전이면, 1 행이 바뀐다.")
        @Transactional
        @Test
        fun affectsOneRow_whenAvailable() {
            // arrange
            val saved = userCouponRepository.save(UserCouponModel.issue(userId = 1L, coupon = savedCoupon()))

            // act
            val affected = userCouponRepository.use(id = saved.id, userId = 1L, now = ZonedDateTime.now())

            // assert
            assertAll(
                { assertThat(affected).isEqualTo(1) },
                { assertThat(userCouponRepository.findByIdAndUserId(saved.id, 1L)?.usedAt).isNotNull },
            )
        }

        @DisplayName("이미 소모했으면, 0 행이다.")
        @Transactional
        @Test
        fun affectsNoRow_whenAlreadyUsed() {
            // arrange
            val saved = userCouponRepository.save(UserCouponModel.issue(userId = 1L, coupon = savedCoupon()))
            userCouponRepository.use(id = saved.id, userId = 1L, now = ZonedDateTime.now())

            // act
            val affected = userCouponRepository.use(id = saved.id, userId = 1L, now = ZonedDateTime.now())

            // assert
            assertThat(affected).isEqualTo(0)
        }

        @DisplayName("만료됐으면, 0 행이다.")
        @Transactional
        @Test
        fun affectsNoRow_whenExpired() {
            // arrange
            val expired = savedCoupon(expiresAt = ZonedDateTime.now().minusDays(1))
            val saved = userCouponRepository.save(UserCouponModel.issue(userId = 1L, coupon = expired))

            // act
            val affected = userCouponRepository.use(id = saved.id, userId = 1L, now = ZonedDateTime.now())

            // assert
            assertThat(affected).isEqualTo(0)
        }

        @DisplayName("남의 쿠폰이면, 0 행이다.")
        @Transactional
        @Test
        fun affectsNoRow_whenOwnedByAnotherUser() {
            // arrange
            val saved = userCouponRepository.save(UserCouponModel.issue(userId = 1L, coupon = savedCoupon()))

            // act
            val affected = userCouponRepository.use(id = saved.id, userId = 999L, now = ZonedDateTime.now())

            // assert
            assertAll(
                { assertThat(affected).isEqualTo(0) },
                { assertThat(userCouponRepository.findByIdAndUserId(saved.id, 1L)?.usedAt).isNull() },
            )
        }
    }

    /**
     * discount_value 의 양수 제약이 스키마에 살아 있는지 확인한다.
     *
     * 네이티브 UPDATE 로 찌르는 것이 이 테스트의 핵심이다. 값 객체와 애플리케이션 검증(생성자 init 블록)을
     * 모두 우회해야 CHECK 제약만 남고, 그래야 이 단언이 제약 자체를 보는 것이 된다.
     * CouponModel·UserCouponModel 의 Check 애노테이션이 사라지면 UPDATE 가 성공해 여기서 깨진다.
     *
     * 두 테이블을 한 케이스에 묶는 이유는 같은 이유(할인값은 0 이면 할인이 아니다)로 같은 모양의
     * 제약을 갖고 있어서다. 테이블명·컬럼명을 파라미터로 받으면 제약이 늘어날 때 케이스 한 줄이면 된다.
     */
    @DisplayName("discount_value 를 0 으로 만드는 네이티브 UPDATE 는, ")
    @Nested
    inner class DiscountValueCheckConstraints {
        @DisplayName("CHECK 제약에 걸려 실패한다.")
        @ParameterizedTest(name = "{0}")
        @CsvSource(
            "user_coupons, ck_user_coupons_discount_value_positive",
            "coupons, ck_coupons_discount_value_positive",
        )
        @Transactional
        fun rejectsZeroValue(table: String, constraintName: String) {
            // arrange
            val id = persistRowFor(table)

            // act
            val result = assertThrows<Exception> { update(table, 0, id) }

            // assert — 아무 예외나 통과시키지 않도록 제약 이름이 예외 사슬에 나타나는지까지 본다
            assertThat(messageChain(result)).contains(constraintName)
        }

        @DisplayName("1 로 만드는 UPDATE 는 통과한다.")
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = ["user_coupons", "coupons"])
        @Transactional
        fun allowsPositiveValue(table: String) {
            // arrange
            val id = persistRowFor(table)

            // act
            val affected = update(table, 1, id)

            // assert
            assertThat(affected).isEqualTo(1)
        }

        /** 테이블별로 대상 행을 하나 만들고 그 id 를 돌려준다. */
        private fun persistRowFor(table: String): Long = when (table) {
            "coupons" -> savedCoupon().id
            "user_coupons" -> userCouponRepository.save(
                UserCouponModel.issue(userId = 1L, coupon = savedCoupon()),
            ).id
            else -> error("알 수 없는 테이블: $table")
        }

        /** 테이블명·컬럼명은 테스트가 소유한 상수뿐이라 문자열로 끼워도 외부 입력이 닿지 않는다. */
        private fun update(table: String, value: Long, id: Long): Int =
            entityManager
                .createNativeQuery("UPDATE $table SET discount_value = :value WHERE id = :id")
                .setParameter("value", value)
                .setParameter("id", id)
                .executeUpdate()

        private fun messageChain(e: Throwable): String =
            generateSequence(e) { it.cause }.mapNotNull { it.message }.joinToString(" | ")
    }
}
