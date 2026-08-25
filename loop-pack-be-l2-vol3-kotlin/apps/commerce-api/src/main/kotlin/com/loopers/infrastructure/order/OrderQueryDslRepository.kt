package com.loopers.infrastructure.order

import com.loopers.domain.order.OrderCriteria
import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.QOrderItemModel.orderItemModel
import com.loopers.domain.order.QOrderModel.orderModel
import com.loopers.domain.support.PageResult
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 주문의 동적 조회를 담당한다.
 *
 * 목록 조회(search)는 항목과 조인하지 않는다. 단건 조회(findById)는 조인한다 — 이유는 각 메서드의
 * KDoc 을 따로 참고한다.
 *
 * 날짜를 시각 경계로 바꾸는 일이 여기 있는 이유는, 도메인 계약이 날짜 범위만 알고
 * created_at 이 시각이라는 사실은 몰라야 하기 때문이다. (설계 문서 4.4 장)
 */
@Component
class OrderQueryDslRepository(
    private val queryFactory: JPAQueryFactory,
) {
    /**
     * 단건 조회는 항목을 fetch join 으로 함께 끌어온다. (Ruling T5-1)
     *
     * getOrder 가 반환하는 애그리거트는 호출자의 트랜잭션이 끝난 뒤에도 .items 를 읽을 수 있어야 한다 —
     * 주문 상세가 실제로 그렇게 쓰기 때문이다. orderItems 는 LAZY 라 트랜잭션 밖에서 읽으면
     * LazyInitializationException 이 나므로, 여기서 미리 초기화해 내보낸다.
     *
     * 목록 조회(search)는 여기와 다르다. 설계 문서 4.2 장대로 목록 응답은 항목을 담지 않으므로 조인이
     * 기여할 것이 없고, 조인하면 항목 수만큼 주문이 중복되어 페이징이 깨진다. 그래서 목록에는 이 조인을
     * 옮기지 않는다.
     *
     * distinct() 가 필요한 이유: LEFT JOIN FETCH 는 항목 수만큼 행을 반환한다(주문 1건 + 항목 2개 = 2행).
     * distinct 없이 fetchOne() 을 부르면 같은 주문을 가리키는 행이 둘로 보여 예외가 난다.
     * distinct 는 같은 루트 엔티티를 가리키는 행을 하나로 접어 fetchOne() 이 성립하게 한다.
     */
    fun findById(id: Long): OrderModel? {
        return queryFactory
            .selectFrom(orderModel)
            .distinct()
            .leftJoin(orderModel.orderItems, orderItemModel).fetchJoin()
            .where(orderModel.id.eq(id), orderModel.deletedAt.isNull)
            .fetchOne()
    }

    fun search(criteria: OrderCriteria.Search): PageResult<OrderModel> {
        val conditions = arrayOf(
            orderModel.deletedAt.isNull,
            orderModel.userId.eq(criteria.userId),
            createdAtGoe(criteria.startAt),
            createdAtLt(criteria.endAt),
        )

        val content = queryFactory
            .selectFrom(orderModel)
            .where(*conditions)
            // id DESC 보조 정렬은 같은 시각의 주문이 여럿일 때 페이지 경계에서 중복과 누락을 막는다.
            .orderBy(orderModel.createdAt.desc(), orderModel.id.desc())
            .offset(criteria.pageQuery.offset)
            .limit(criteria.pageQuery.size.toLong())
            .fetch()

        // 마지막 페이지를 넘어선 요청에서도 totalElements 는 유지되어야 하므로, content 가 비어도 count 는 센다.
        val totalElements = queryFactory
            .select(orderModel.count())
            .from(orderModel)
            .where(*conditions)
            .fetchOne() ?: 0L

        return PageResult.of(content = content, pageQuery = criteria.pageQuery, totalElements = totalElements)
    }

    private fun createdAtGoe(startAt: LocalDate?): BooleanExpression? =
        startAt?.let { orderModel.createdAt.goe(it.atStartOfDay(ZoneId.systemDefault())) }

    /**
     * endAt 다음 날 00:00 미만으로 거른다. endAt 당일을 포함하기 위해서다. (설계 문서 4.4 장)
     * loe(endAt 00:00) 으로 쓰면 그날 주문이 통째로 빠진다.
     */
    private fun createdAtLt(endAt: LocalDate?): BooleanExpression? =
        endAt?.let { orderModel.createdAt.lt(endOfDayExclusive(it)) }

    private fun endOfDayExclusive(date: LocalDate): ZonedDateTime =
        date.plusDays(1).atStartOfDay(ZoneId.systemDefault())
}
