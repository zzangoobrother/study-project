package com.loopers.interfaces.api

import com.loopers.domain.support.PageResult

/**
 * 목록 API 공통 응답 표현.
 *
 * Spring Data 의 Page 를 그대로 직렬화하지 않는 이유는 pageable / sort / numberOfElements 같은
 * 내부 구조가 응답 계약이 되어버리기 때문이다. Spring Boot 3 도 이 직렬화를 불안정하다고 경고한다.
 *
 * ApiResponse 와 같은 패키지에 두어 이후 모든 목록 API 가 같은 계약을 쓰게 한다.
 */
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun <T, R> from(result: PageResult<T>, transform: (T) -> R): PageResponse<R> {
            return PageResponse(
                content = result.content.map(transform),
                page = result.page,
                size = result.size,
                totalElements = result.totalElements,
                totalPages = result.totalPages,
            )
        }
    }
}
