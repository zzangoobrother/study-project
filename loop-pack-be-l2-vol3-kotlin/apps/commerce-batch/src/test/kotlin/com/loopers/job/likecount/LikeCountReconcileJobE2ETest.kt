package com.loopers.job.likecount

import com.loopers.batch.job.likecount.LikeCountReconcileJobConfig
import com.loopers.batch.job.likecount.LikeCountReconcileRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.batch.test.MetaDataInstanceFactory
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.CannotAcquireLockException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.context.jdbc.Sql

@SpringBootTest
@SpringBatchTest
@TestPropertySource(
    properties = [
        "spring.batch.job.name=${LikeCountReconcileJobConfig.JOB_NAME}",
        "spring.batch.job.enabled=false",
    ],
)
@Sql(scripts = ["/sql/like-count-reconcile-schema.sql"])
class LikeCountReconcileJobE2ETest @Autowired constructor(
    // IDE 정적 분석 상 [SpringBatchTest] 의 주입보다 [SpringBootTest] 의 주입이 우선되어 오류처럼 보일 수 있으나 정상 동작한다. (DemoJobE2ETest 와 같다)
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @param:Qualifier(LikeCountReconcileJobConfig.JOB_NAME) private val job: Job,
    private val jobExplorer: JobExplorer,
    jdbcTemplate: JdbcTemplate,
) {
    @MockitoSpyBean
    private lateinit var repository: LikeCountReconcileRepository

    private val tables = LikeCountTables(jdbcTemplate)

    /**
     * SpringBatchTest 의 JobScopeTestExecutionListener 는 이 클래스에서 JobExecution 을 반환하는 메서드를 찾아
     * 매 테스트 전에 리플렉션으로 호출해 Job 스코프 컨텍스트를 만든다. 이름 일치가 우선순위라 "getJobExecution" 이
     * 없으면 launch / launchLikeBootRunner 중 하나가 대신 뽑혀 매 테스트마다 실제 잡을 몰래 한 번 더 실행하고,
     * run.id 파라미터가 충돌해 JobInstanceAlreadyCompleteException 으로 테스트가 깨진다.
     * 이 더미를 두어 실제 실행 없이 스코프만 열리게 한다.
     */
    private fun getJobExecution(): JobExecution = MetaDataInstanceFactory.createJobExecution()

    @AfterEach
    fun tearDown() {
        tables.truncate()
    }

    /** 어긋난 상품 1·2, 정합한 상품 3, 어긋났지만 삭제된 상품 4. */
    private fun arrangeProducts() {
        tables.insertProduct(id = 1L, likeCount = 5L) // 실제 0
        tables.insertProduct(id = 2L, likeCount = 0L) // 실제 2
        tables.insertLike(productId = 2L, userId = 1L)
        tables.insertLike(productId = 2L, userId = 2L)
        tables.insertProduct(id = 3L, likeCount = 1L) // 실제 1
        tables.insertLike(productId = 3L, userId = 1L)
        tables.insertProduct(id = 4L, likeCount = 7L, deleted = true)
    }

    private fun launch(dryRun: String?): JobExecution {
        jobLauncherTestUtils.job = job
        val builder = jobLauncherTestUtils.uniqueJobParametersBuilder
        dryRun?.let { builder.addString("dryRun", it) }
        return jobLauncherTestUtils.launchJob(builder.toJobParameters())
    }

    /**
     * `--job.name` 기동 때 Boot 의 JobLauncherApplicationRunner 가 파라미터를 만드는 방식과 같다 —
     * 직전 실행의 파라미터를 잡의 incrementer 에 넘긴다. uniqueJobParametersBuilder 는 incrementer 를 거치지 않는다.
     */
    private fun launchLikeBootRunner(): JobExecution {
        jobLauncherTestUtils.job = job
        return jobLauncherTestUtils.launchJob(JobParametersBuilder(jobExplorer).getNextJobParameters(job).toJobParameters())
    }

    @DisplayName("dryRun 을 주지 않으면, ")
    @Nested
    inner class DefaultDryRun {
        @DisplayName("후보만 세고 아무것도 덮어쓰지 않는다.")
        @Test
        fun countsCandidatesWithoutWriting() {
            // arrange
            arrangeProducts()

            // act
            val execution = launch(dryRun = null)

            // assert
            val step = execution.stepExecutions.single()
            assertAll(
                { assertThat(execution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
                { assertThat(step.readCount).isEqualTo(2L) },
                { assertThat(step.writeCount).isEqualTo(0L) },
                { assertThat(tables.likeCountOf(1L)).isEqualTo(5L) },
                { assertThat(tables.likeCountOf(2L)).isEqualTo(0L) },
            )
        }

        @DisplayName("직전 실행이 dryRun=false 였어도, 이전 파라미터를 이어받지 않아 덮어쓰지 않는다.")
        @Test
        fun doesNotCarryOverDryRun_whenOmittedOnNextLaunch() {
            // arrange — 한 번 보정한 뒤 새로 어긋난 상품을 만든다
            arrangeProducts()
            launch(dryRun = "false")
            tables.insertProduct(id = 5L, likeCount = 4L) // 실제 0

            // act
            val execution = launchLikeBootRunner()

            // assert
            val step = execution.stepExecutions.single()
            assertAll(
                { assertThat(execution.jobParameters.getString("dryRun")).isNull() },
                { assertThat(execution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
                { assertThat(step.readCount).isEqualTo(1L) },
                { assertThat(step.writeCount).isEqualTo(0L) },
                { assertThat(tables.likeCountOf(5L)).isEqualTo(4L) },
            )
        }
    }

    @DisplayName("dryRun=false 로 실행하면, ")
    @Nested
    inner class Apply {
        @DisplayName("어긋난 상품만 활성 좋아요 수로 덮어쓴다.")
        @Test
        fun correctsOnlyMismatchedLiveProducts() {
            // arrange
            arrangeProducts()

            // act
            val execution = launch(dryRun = "false")

            // assert
            val step = execution.stepExecutions.single()
            assertAll(
                { assertThat(execution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
                { assertThat(step.readCount).isEqualTo(2L) },
                { assertThat(step.writeCount).isEqualTo(2L) },
                { assertThat(tables.likeCountOf(1L)).isEqualTo(0L) },
                { assertThat(tables.likeCountOf(2L)).isEqualTo(2L) },
                { assertThat(tables.likeCountOf(3L)).isEqualTo(1L) },
                { assertThat(tables.likeCountOf(4L)).isEqualTo(7L) },
            )
        }

        @DisplayName("보정 직후 다시 실행하면, 후보 0 건으로 정상 종료한다.")
        @Test
        fun findsNothing_whenRunAgainAfterCorrection() {
            // arrange
            arrangeProducts()
            launch(dryRun = "false")

            // act
            val second = launch(dryRun = "false")

            // assert
            val step = second.stepExecutions.single()
            assertAll(
                { assertThat(second.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
                { assertThat(step.readCount).isEqualTo(0L) },
                { assertThat(step.writeCount).isEqualTo(0L) },
            )
        }

        @DisplayName("한 상품이 실패해도 나머지는 보정하고, 잡은 FAILED 로 끝난다.")
        @Test
        fun correctsOthersAndFails_whenOneProductFails() {
            // arrange
            arrangeProducts()
            doThrow(CannotAcquireLockException("락 대기 시간 초과")).whenever(repository).lockLikeCount(1L)

            // act
            val execution = launch(dryRun = "false")

            // assert
            val step = execution.stepExecutions.single()
            assertAll(
                { assertThat(execution.exitStatus.exitCode).isEqualTo(ExitStatus.FAILED.exitCode) },
                { assertThat(step.readCount).isEqualTo(2L) },
                { assertThat(step.writeCount).isEqualTo(1L) },
                { assertThat(tables.likeCountOf(1L)).isEqualTo(5L) },
                { assertThat(tables.likeCountOf(2L)).isEqualTo(2L) },
            )
        }
    }

    @DisplayName("dryRun 이 true / false 가 아니면, ")
    @Nested
    inner class InvalidDryRun {
        @DisplayName("아무것도 덮어쓰지 않고 잡이 실패한다.")
        @Test
        fun failsWithoutWriting_whenDryRunIsNotBoolean() {
            // arrange
            arrangeProducts()

            // act
            val execution = launch(dryRun = "flase")

            // assert
            assertAll(
                { assertThat(execution.exitStatus.exitCode).isEqualTo(ExitStatus.FAILED.exitCode) },
                { assertThat(tables.likeCountOf(1L)).isEqualTo(5L) },
                { assertThat(tables.likeCountOf(2L)).isEqualTo(0L) },
            )
        }
    }
}
