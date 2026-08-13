package com.loopers.domain.support

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

/**
 * 페이징 요청.
 *
 * 이 객체가 존재한다는 것 자체가 page/size 검증 통과를 의미하므로, 하위 계층은 값을 다시 확인하지 않는다.
 * size 상한이 방어의 핵심이다. 상한이 없으면 ?size=1000000 한 번으로 테이블 전체를 메모리에 올리게 할 수 있다.
 */
data class PageQuery(
    val page: Int = DEFAULT_PAGE,
    val size: Int = DEFAULT_SIZE,
) {
    init {
        if (page < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "페이지 번호는 0 이상이어야 합니다.")
        }
        if (size !in MIN_SIZE..MAX_SIZE) {
            throw CoreException(ErrorType.BAD_REQUEST, "페이지 크기는 $MIN_SIZE 이상 $MAX_SIZE 이하여야 합니다.")
        }
    }

    /** Int 곱셈은 깊은 페이지에서 넘칠 수 있어 Long 으로 계산한다. */
    val offset: Long get() = page.toLong() * size

    companion object {
        const val DEFAULT_PAGE = 0
        const val DEFAULT_SIZE = 20
        const val MIN_SIZE = 1
        const val MAX_SIZE = 100

        /**
         * 쿼리 파라미터가 생략된 경우를 처리한다.
         * 기본값이 이 한 곳에만 존재하도록 컨트롤러에는 @RequestParam(defaultValue = ...) 를 두지 않는다.
         */
        fun of(page: Int?, size: Int?): PageQuery = PageQuery(page ?: DEFAULT_PAGE, size ?: DEFAULT_SIZE)
    }
}
