package com.loopers.batch.job.likecount.step

import com.loopers.batch.job.likecount.LikeCountReconcileJobConfig
import com.loopers.batch.job.likecount.LikeCountReconciler
import com.loopers.batch.job.likecount.ReconcileOutcome
import org.slf4j.LoggerFactory
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Component
import org.springframework.transaction.TransactionException

/**
 * 좋아요 수 보정 스텝.
 *
 * Chunk 가 아니라 Tasklet 이다. 탐지 쿼리는 product_likes 풀스캔 집계라 페이징 리더가 페이지마다 다시 실행하면
 * 풀스캔이 페이지 수만큼 반복된다. 후보는 Long 목록이라 한 번에 읽어도 된다. (2026-09-28 설계 문서 2.1 장)
 *
 * dryRun 기본값은 true 다. 측정용 시드(loadtest/seed-products.sql)는 좋아요 행 없이 합성 like_count 를 심으므로,
 * 그 DB 에서 한 번 덮어쓰면 측정 데이터가 영구히 망가진다. (2026-09-28 설계 문서 2.2 장)
 */
@StepScope
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = LikeCountReconcileJobConfig.JOB_NAME)
@Component
class LikeCountReconcileTasklet(
    private val reconciler: LikeCountReconciler,
    @param:Value("#{jobParameters['dryRun']}") private val dryRunParameter: String?,
) : Tasklet {
    private val log = LoggerFactory.getLogger(LikeCountReconcileTasklet::class.java)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        // 탐지보다 먼저 해석한다. 잘못된 값이면 아무것도 읽거나 쓰기 전에 끝난다.
        val dryRun = parseDryRun(dryRunParameter)

        val candidates = reconciler.findCandidateProductIds()
        // StepContribution 에는 읽기 건수를 한 번에 더하는 메서드가 없다.
        repeat(candidates.size) { contribution.incrementReadCount() }

        if (dryRun) {
            log.warn(
                "[dryRun] 좋아요 수 불일치 후보 {} 건 — 재검증 전이라 진행 중이던 좋아요로 인한 일시적 불일치가 섞일 수 있다. " +
                    "앞 {} 건 : {}",
                candidates.size,
                SAMPLE_SIZE,
                candidates.take(SAMPLE_SIZE),
            )
            return RepeatStatus.FINISHED
        }

        var failures = 0
        candidates.forEach { productId ->
            try {
                val outcome = reconciler.reconcile(productId)
                if (outcome is ReconcileOutcome.Corrected) {
                    // 어긋남은 그 자체로 버그 신호다. INFO 로 묻히지 않게 WARN 으로 남긴다. (2026-09-28 설계 문서 3.2 장)
                    log.warn("좋아요 수 보정 : productId={}, {} -> {}", productId, outcome.before, outcome.after)
                    contribution.incrementWriteCount(1)
                }
            } catch (e: DataAccessException) {
                failures++
                log.error("좋아요 수 보정 실패 : productId={}", productId, e)
            } catch (e: TransactionException) {
                failures++
                log.error("좋아요 수 보정 실패 : productId={}", productId, e)
            }
        }

        // 한 건 때문에 나머지를 멈추지 않되, 스텝은 FAILED 로 끝내 재실행 대상임을 드러낸다.
        // 이미 보정한 상품은 상품마다 커밋됐으므로 남는다.
        check(failures == 0) { "좋아요 수 보정 실패 $failures 건 — 로그를 확인하고 재실행한다." }
        return RepeatStatus.FINISHED
    }

    /** "flase".toBoolean() 은 false 다. 기본 변환을 쓰면 오타가 곧 덮어쓰기가 되므로 두 값만 받는다. */
    private fun parseDryRun(raw: String?): Boolean =
        when (raw?.lowercase()) {
            null, "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("dryRun 은 true 또는 false 여야 합니다 : $raw")
        }

    private companion object {
        const val SAMPLE_SIZE = 20
    }
}
