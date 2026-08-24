package com.loopers.infrastructure.order

import com.loopers.domain.order.OrderModel
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 단건 조회 메서드를 여기 두지 않는다. (Ruling T5-1)
 *
 * 단건 조회는 항목(orderItems)을 fetch join 으로 함께 끌어와야 하는데, 파생 쿼리 메서드가 반환하는
 * 컬렉션이 to-many 라 fetch join + distinct 없이는 항목이 둘 이상일 때 NonUniqueResultException 이 난다.
 * 파생 쿼리 메서드 이름만으로는 distinct 를 표현할 방법이 마땅치 않아, 그 조합을 명시적으로 다룰 수 있는
 * `OrderQueryDslRepository.findById` 로 옮겼다. 그래서 이 인터페이스는 `save` 를 위한
 * `JpaRepository` 상속만 남는다.
 */
interface OrderJpaRepository : JpaRepository<OrderModel, Long>
