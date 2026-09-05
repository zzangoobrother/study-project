package com.loopers.infrastructure.coupon

/**
 * GROUP BY 집계의 행 하나. Spring Data 의 인터페이스 프로젝션이다.
 *
 * 인프라 계층에만 존재한다. 도메인 저장소는 Map<Long, Long> 으로 받으므로
 * 이 타입이 도메인 계약에 등장하지 않는다.
 */
interface CouponIssueCount {
    val couponId: Long
    val issuedCount: Long
}
