package com.loopers.infrastructure.order

import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.ZonedDateTime

/**
 * 단건 조회 메서드를 여기 두지 않는다. (Ruling T5-1)
 *
 * 단건 조회는 항목(orderItems)을 fetch join 으로 함께 끌어와야 하는데, 파생 쿼리 메서드가 반환하는
 * 컬렉션이 to-many 라 fetch join + distinct 없이는 항목이 둘 이상일 때 NonUniqueResultException 이 난다.
 * 파생 쿼리 메서드 이름만으로는 distinct 를 표현할 방법이 마땅치 않아, 그 조합을 명시적으로 다룰 수 있는
 * `OrderQueryDslRepository.findById` 로 옮겼다. 그래서 이 인터페이스에는 `save` 를 위한 상속과
 * 상태 전이용 조건부 UPDATE 만 남는다.
 */
interface OrderJpaRepository : JpaRepository<OrderModel, Long> {
    /**
     * 결제 상태 전이. 영향 행 수가 곧 판정이다 — 0 이면 다른 요청이 먼저 바꿨다. (2026-10-13 결제 설계 3.3 장)
     *
     * decreaseStock 과 같이 updated_at 을 건드리지 않는다.
     * clearAutomatically 이므로 호출 뒤 같은 트랜잭션의 엔티티는 detach 된다. 필요한 값은 호출 전에 꺼낸다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE OrderModel o
           SET o.status = :next
         WHERE o.id = :orderId AND o.deletedAt IS NULL AND o.status IN :expected
        """,
    )
    fun changeStatus(
        @Param("orderId") orderId: Long,
        @Param("expected") expected: Collection<OrderStatus>,
        @Param("next") next: OrderStatus,
    ): Int

    /** 만료 대상 ID. createdAt 경계는 포함(<=)이다. limit 은 Pageable 로 번역해 받는다. */
    @Query(
        """
        SELECT o.id FROM OrderModel o
         WHERE o.status IN :statuses AND o.createdAt <= :createdBefore AND o.deletedAt IS NULL
         ORDER BY o.id
        """,
    )
    fun findExpirationTargetIds(
        @Param("statuses") statuses: Collection<OrderStatus>,
        @Param("createdBefore") createdBefore: ZonedDateTime,
        pageable: Pageable,
    ): List<Long>
}
