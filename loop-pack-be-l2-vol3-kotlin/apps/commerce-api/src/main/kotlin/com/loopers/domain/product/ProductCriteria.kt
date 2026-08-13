package com.loopers.domain.product

import com.loopers.domain.support.PageQuery

/**
 * 상품 도메인의 조회 조건 전달 객체.
 *
 * 도메인에 두어 서비스 시그니처가 상위 계층 타입에 의존하지 않도록 한다.
 * 검증을 마친 값만 담으므로 이 객체가 존재한다는 것 자체가 파라미터 검증 통과를 의미한다.
 */
class ProductCriteria {
    data class Search(
        /** null 이면 전체 브랜드를 대상으로 한다. 없는 브랜드 ID 도 오류가 아니라 빈 결과다. */
        val brandId: Long?,
        val sort: ProductSortType,
        val pageQuery: PageQuery,
    )
}
