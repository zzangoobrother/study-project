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

    /**
     * 어드민 상품 목록의 조회 조건.
     *
     * 정렬 필드가 없는 것은 어드민 목록의 정렬이 최신순 고정이기 때문이다. (요구사항에 sort 파라미터가 없다)
     * Search 와 달리 소프트 삭제된 상품도 결과에 포함된다.
     */
    data class AdminSearch(
        /** null 이면 전체 브랜드를 대상으로 한다. 없는 브랜드 ID 도 오류가 아니라 빈 결과다. */
        val brandId: Long?,
        val pageQuery: PageQuery,
    )
}
