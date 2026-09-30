package com.loopers.batch.job.likecount

import com.loopers.batch.job.likecount.step.LikeCountReconcileTasklet
import com.loopers.batch.listener.JobListener
import com.loopers.batch.listener.StepMonitorListener
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.JobParametersIncrementer
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.JobScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.support.transaction.ResourcelessTransactionManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 좋아요 수 보정 잡. `--job.name=likeCountReconcileJob` 으로 기동하고, 덮어쓰려면 `dryRun=false` 를 명시한다.
 *
 * 스텝 트랜잭션이 ResourcelessTransactionManager 인 것은 의도다. 실제 DB 트랜잭션은
 * LikeCountReconciler.reconcile 이 상품마다 따로 연다. 스텝 전체를 한 트랜잭션으로 감싸면
 * 앞에서 잠근 상품 행이 스텝이 끝날 때까지 풀리지 않는다. (2026-09-28 설계 문서 3.2 장)
 */
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = LikeCountReconcileJobConfig.JOB_NAME)
@Configuration
class LikeCountReconcileJobConfig(
    private val jobRepository: JobRepository,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val likeCountReconcileTasklet: LikeCountReconcileTasklet,
) {
    companion object {
        const val JOB_NAME = "likeCountReconcileJob"
        private const val STEP_NAME = "likeCountReconcileStep"
        private const val RUN_ID = "run.id"

        /**
         * run.id 만 새로 만들고 직전 실행의 나머지 파라미터는 버린다.
         *
         * RunIdIncrementer 를 쓰지 않는 이유: Boot 러너는 직전 실행의 파라미터를 incrementer 에 넘기고, RunIdIncrementer 는
         * 그것을 복사한 채 run.id 만 올린다. 어제 dryRun=false 로 돌렸다면 오늘 dryRun 을 생략해도 false 가 이어져 덮어쓰기가 된다.
         * (2026-09-28 설계 문서 2.2 장)
         */
        private val RUN_ID_ONLY_INCREMENTER = JobParametersIncrementer { previous ->
            val lastRunId = previous?.getLong(RUN_ID) ?: 0L
            JobParametersBuilder().addLong(RUN_ID, lastRunId + 1).toJobParameters()
        }
    }

    @Bean(JOB_NAME)
    fun likeCountReconcileJob(): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RUN_ID_ONLY_INCREMENTER)
            .start(likeCountReconcileStep())
            .listener(jobListener)
            .build()

    @JobScope
    @Bean(STEP_NAME)
    fun likeCountReconcileStep(): Step =
        StepBuilder(STEP_NAME, jobRepository)
            .tasklet(likeCountReconcileTasklet, ResourcelessTransactionManager())
            .listener(stepMonitorListener)
            .build()
}
