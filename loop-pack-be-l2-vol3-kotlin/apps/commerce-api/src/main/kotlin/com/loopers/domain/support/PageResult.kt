package com.loopers.domain.support

/**
 * 페이징 조회 결과.
 *
 * Spring Data 의 Page 를 쓰지 않는 이유는 도메인 계층이 영속화 기술에 의존하지 않게 하기 위해서다.
 * Repository 인터페이스는 도메인 패키지에 있으므로, 그 시그니처에 Spring Data 타입이 등장하면 전제가 깨진다.
 */
data class PageResult<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
) {
    /** totalElements 와 어긋날 수 없도록 저장하지 않고 계산한다. 0건이면 0페이지다(1이 아니다). */
    val totalPages: Int get() = if (totalElements == 0L) 0 else ((totalElements - 1) / size + 1).toInt()

    fun <R> map(transform: (T) -> R): PageResult<R> =
        PageResult(content = content.map(transform), page = page, size = size, totalElements = totalElements)

    companion object {
        fun <T> of(content: List<T>, pageQuery: PageQuery, totalElements: Long): PageResult<T> =
            PageResult(content = content, page = pageQuery.page, size = pageQuery.size, totalElements = totalElements)
    }
}
