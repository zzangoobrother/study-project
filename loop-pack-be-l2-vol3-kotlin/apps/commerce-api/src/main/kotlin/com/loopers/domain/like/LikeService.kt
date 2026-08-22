package com.loopers.domain.like

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

/**
 * 좋아요 애그리거트의 유스케이스.
 *
 * 이 서비스는 상품도 회원도 모른다. 좋아요 수를 움직이는 것은 이 애그리거트의 일이 아니며,
 * 두 애그리거트를 잇는 책임은 LikeFacade 에만 있다. (설계 문서 3.2 장)
 */
@Component
class LikeService(
    private val productLikeRepository: ProductLikeRepository,
) {
    /**
     * 좋아요를 건다. 반환값은 "이 호출이 상태를 바꿨는가" 다.
     *
     * false 는 실패가 아니라 "이미 좋아요 상태였다" 는 뜻이다.
     * 호출자는 이 값이 true 일 때만 좋아요 수를 올려야 한다. 아니면 중복 요청마다 수가 늘어난다.
     *
     * 선조회를 두는 이유는 성능이 아니다. 없으면 흔한 더블클릭이 매번 유니크 제약 위반 예외를 일으켜
     * 정상 동작이 예외 경로를 타게 된다. 동시 최초 좋아요에서 두 요청이 모두 "행 없음" 을 보는 경우는
     * 여전히 남으며, 그때 진 쪽의 예외는 LikeFacade 가 흡수한다. (설계 문서 6.6, 6.8 장)
     */
    @Transactional
    fun like(userId: Long, productId: Long): Boolean {
        val existing = productLikeRepository.findIncludingDeleted(userId = userId, productId = productId)

        return when {
            existing == null -> {
                productLikeRepository.save(ProductLikeModel.create(userId = userId, productId = productId))
                true
            }

            existing.deletedAt != null ->
                productLikeRepository.restore(userId = userId, productId = productId, now = ZonedDateTime.now()) == 1

            else -> false
        }
    }

    /**
     * 좋아요를 취소한다. 반환값은 "이 호출이 상태를 바꿨는가" 다.
     *
     * 등록과 달리 선조회가 없다. INSERT 가 없어 유니크 제약 위반이 발생할 수 없고,
     * 조건부 UPDATE 한 문장이 판정과 전이를 동시에 끝낸다. (설계 문서 6.7 장)
     */
    @Transactional
    fun unlike(userId: Long, productId: Long): Boolean {
        return productLikeRepository.softDelete(userId = userId, productId = productId, now = ZonedDateTime.now()) == 1
    }
}
