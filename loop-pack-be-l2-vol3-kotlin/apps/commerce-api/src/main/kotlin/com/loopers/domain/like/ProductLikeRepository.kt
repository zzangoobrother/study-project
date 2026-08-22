package com.loopers.domain.like

import java.time.ZonedDateTime

/**
 * 좋아요 저장소.
 *
 * restore 와 softDelete 가 엔티티를 받지 않고 식별자와 시각을 받는 것이 이 인터페이스의 핵심이다.
 * 엔티티를 읽어 상태를 바꾸면 읽기와 쓰기 사이의 틈에서 갱신 손실이 생기므로 (설계 문서 6.2 장),
 * 두 연산은 조건을 WHERE 절에 담은 단일 UPDATE 여야 한다.
 * 그래서 반환이 Unit 이 아니라 영향 행 수이며, 그 숫자가 전이 여부의 유일한 근거다.
 */
interface ProductLikeRepository {
    fun save(productLike: ProductLikeModel): ProductLikeModel

    /**
     * 삭제 여부와 무관하게 조회한다. 등록 경로의 선조회 전용이다.
     * "행이 없다" 와 "취소된 행이 있다" 를 구분해야 하므로 삭제된 행도 보아야 한다.
     */
    fun findIncludingDeleted(userId: Long, productId: Long): ProductLikeModel?

    /** 취소된 좋아요를 되살린다. 이미 살아 있으면 아무것도 바꾸지 않는다. 반환값은 영향 행 수다. */
    fun restore(userId: Long, productId: Long, now: ZonedDateTime): Int

    /** 살아 있는 좋아요를 취소한다. 이미 취소됐거나 행이 없으면 아무것도 바꾸지 않는다. 반환값은 영향 행 수다. */
    fun softDelete(userId: Long, productId: Long, now: ZonedDateTime): Int
}
