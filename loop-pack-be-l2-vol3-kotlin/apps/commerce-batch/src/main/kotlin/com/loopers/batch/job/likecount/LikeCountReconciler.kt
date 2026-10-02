package com.loopers.batch.job.likecount

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 상품 하나의 좋아요 수를 재검증하고 보정한다.
 *
 * 순서가 전부다 — 상품 행을 먼저 잠그고, 그 뒤에 락 없이 센다. LikeFacade 는 좋아요 행 변경과 카운트 갱신을
 * 한 트랜잭션에서 하고 상품 행 락을 커밋까지 쥐므로, 이 순서면 진행 중인 좋아요 트랜잭션이 어느 지점에 있어도 최종값이 맞는다.
 * (2026-09-28 설계 문서 3.3 장)
 *
 * READ_COMMITTED 를 명시하는 이유: 세는 SELECT 의 스냅샷이 잠금 이후에 잡혀야 한다.
 * REPEATABLE READ 에서도 지금 순서로는 우연히 맞지만, 잠금 앞에 일반 SELECT 가 하나라도 끼어들면
 * 스냅샷이 잠금 이전으로 당겨져 깨진다. 그 전제를 코드 순서가 아니라 설정이 보장하게 한다.
 *
 * 상품마다 트랜잭션을 끊어야 한 상품의 락이 다음 상품을 처리하는 동안 남지 않는다. (2026-09-28 설계 문서 3.2 장)
 * 지금은 스텝이 ResourcelessTransactionManager 로 돌아 바깥 DB 트랜잭션이 없으므로 REQUIRED 여도 같다.
 * REQUIRES_NEW 는 누군가 스텝 트랜잭션 매니저를 실제 DB 매니저로 바꿨을 때 모든 상품이 한 트랜잭션에 묶이는 것을 막는다.
 */
@Component
class LikeCountReconciler(
    private val repository: LikeCountReconcileRepository,
) {
    fun findCandidateProductIds(): List<Long> = repository.findCandidateProductIds()

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    fun reconcile(productId: Long): ReconcileOutcome {
        val before = repository.lockLikeCount(productId) ?: return ReconcileOutcome.ProductGone
        val actual = repository.countActiveLikes(productId)
        if (before == actual) {
            return ReconcileOutcome.AlreadyConsistent
        }
        repository.updateLikeCount(productId, actual)
        return ReconcileOutcome.Corrected(before = before, after = actual)
    }
}

sealed interface ReconcileOutcome {
    data class Corrected(val before: Long, val after: Long) : ReconcileOutcome

    /** 탐지 시점엔 어긋나 보였지만 잠근 뒤 다시 세니 맞았다. 진행 중이던 좋아요가 그 사이 커밋된 경우다. */
    data object AlreadyConsistent : ReconcileOutcome

    /** 탐지 뒤에 삭제됐다. 삭제된 상품의 카운트는 맞출 대상이 아니다. (2026-08-20 설계 문서 7.4 장) */
    data object ProductGone : ReconcileOutcome
}
