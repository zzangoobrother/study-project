package com.loopers.domain.order

import com.loopers.domain.support.PageQuery
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDate

/**
 * 주문 도메인의 조회 조건.
 *
 * 도메인에 두어 서비스 시그니처가 상위 계층 타입에 의존하지 않도록 한다.
 * 검증을 마친 값만 담으므로 이 객체가 존재한다는 것 자체가 파라미터 검증 통과를 의미한다.
 */
class OrderCriteria {
    /**
     * startAt / endAt 이 nullable 인 것은 기간을 선택으로 두기 때문이다. (설계 문서 4.3 장)
     * 필수로 만들면 "내 주문 전체 보기" 를 하려는 클라이언트가 아무 의미 없는 과거 날짜를 꾸며내야 한다.
     * 응답 크기 방어는 기간이 아니라 PageQuery 의 size 상한이 한다.
     *
     * 두 값이 모두 있을 때 순서가 뒤집혀 있으면 400 이다.
     * 빈 목록으로 응답하면 클라이언트가 "주문이 없다" 와 "범위를 거꾸로 보냈다" 를 구분할 수 없다.
     */
    data class Search(
        val userId: Long,
        val startAt: LocalDate?,
        val endAt: LocalDate?,
        val pageQuery: PageQuery,
    ) {
        init {
            if (startAt != null && endAt != null && startAt.isAfter(endAt)) {
                throw CoreException(ErrorType.BAD_REQUEST, "조회 시작일은 종료일보다 늦을 수 없습니다.")
            }
        }
    }
}
