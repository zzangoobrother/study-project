package com.example.consensus.flp;

import static com.example.consensus.flp.FlpScenarios.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.consensus.sim.SimulationResult;
import com.example.consensus.sim.StopReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 1편에서 100% 결정되던 설정(지연 5 고정, retryTimeout 12, retryJitter 3)을 시간순 스케줄러와 적대자로 각각 돌려 본다.
 * 적대자의 결과는 "maxSteps 안에서"라는 한정이 붙는다. 영원히 막았다는 증명이 아니라, 정해진 step 동안 막았다는 측정이다.
 */
class AdversaryVsTimeOrderTest {

    private static final int ADVERSARY_SEEDS = 50;
    private static final long ADVERSARY_MAX_STEPS = 1_000;

    @Test
    @DisplayName("시간순 스케줄러: 1편 설정(jitter 3)은 시드 1,000개 모두 결정된다")
    void timeOrderDecidesEverySeed() {
        int decided = 0, violations = 0;
        for (long seed = 1; seed <= 1_000; seed++) {
            SimulationResult r = runTimeOrder(source(seed, duel(3)), 3_000);
            if (!r.decisions().isEmpty()) {
                decided++;
            }
            if (r.reason() == StopReason.INVARIANT_VIOLATED) {
                violations++;
            }
        }
        System.out.println("[FLP] timeorder jitter=3 decided=" + decided + "/1000 violations=" + violations);
        assertThat(violations).isZero();
        assertThat(decided).isEqualTo(1_000);
    }

    @Test
    @DisplayName("적대자: 같은 설정에서 maxSteps 안에 결정되지 않고, 공정성과 안전성은 지켜진다")
    void adversaryBlocksDecisionWithinMaxSteps() {
        int decided = 0, violations = 0;
        long maxWait = 0, maxObservedAge = 0, failures = 0, inserts = 0, probeRuns = 0, notDelivered = 0;
        long startNanos = System.nanoTime();
        for (long seed = 1; seed <= ADVERSARY_SEEDS; seed++) {
            AdversaryRun run = runAdversary(source(seed, duel(3)), ADVERSARY_MAX_STEPS, seed);
            if (run.decided()) {
                decided++;
            }
            if (run.violated()) {
                violations++;
            }
            maxWait = Math.max(maxWait, run.adversary().maxWait());
            maxObservedAge = Math.max(maxObservedAge, run.adversary().maxObservedAge());
            failures += run.adversary().failures();
            inserts += run.adversary().inserts();
            probeRuns += run.adversary().stats().probeRuns();
            SimulationResult r = run.result();
            notDelivered += r.sent() - r.delivered() - r.dropped(); // 끝난 시점에 아직 pending에 있는(방금 보낸) 메시지
        }
        long millis = (System.nanoTime() - startNanos) / 1_000_000;

        System.out.println("[FLP] adversary decided=" + decided + "/" + ADVERSARY_SEEDS + " (maxSteps=" + ADVERSARY_MAX_STEPS
                + " 안에서, jitter=3, 지연 5 고정, retryTimeout 12) violations=" + violations);
        System.out.println("[FLP] adversary 공정성: 마감 D=" + DEADLINE + " 끼워넣기 예산=" + INSERT_BUDGET + " 최장 대기 maxWait=" + maxWait
                + " 끝난 시점 pending 최장 나이=" + maxObservedAge + " 끝날 때 pending 메시지 시드 평균=" + notDelivered / ADVERSARY_SEEDS + "개(모두 나이 " + maxObservedAge + " step 이하)");
        System.out.println("[FLP] adversary 비용: 적대자 실패 " + failures + "회, 보조정리 2 끼워넣기 " + inserts + "회, 프로브 실행 "
                + probeRuns + "회, 소요 " + millis + "ms (시드당 " + millis / ADVERSARY_SEEDS + "ms)");

        assertThat(violations).isZero();
        assertThat(decided).isZero();
        assertThat(maxWait).isLessThanOrEqualTo(DEADLINE + INSERT_BUDGET);
        assertThat(maxObservedAge).isLessThanOrEqualTo(DEADLINE + INSERT_BUDGET);
    }

    @Test
    @DisplayName("마감을 짧게(D=12) 줘서 보조정리 2의 끼워 넣기가 실제로 일어나게 해도 결정되지 않는다")
    void shortDeadlineExercisesLemmaTwo() {
        int decided = 0, violations = 0;
        long maxWait = 0, failures = 0, inserts = 0;
        int seeds = 20;
        for (long seed = 1; seed <= seeds; seed++) {
            AdversaryRun run = runAdversary(source(seed, duel(3)), ADVERSARY_MAX_STEPS, seed, 12, 6);
            decided += run.decided() ? 1 : 0;
            violations += run.violated() ? 1 : 0;
            maxWait = Math.max(maxWait, run.adversary().maxWait());
            failures += run.adversary().failures();
            inserts += run.adversary().inserts();
        }
        System.out.println("[FLP] adversary D=12 budget=6 decided=" + decided + "/" + seeds + " maxWait=" + maxWait
                + " 보조정리2 끼워넣기=" + inserts + "회 적대자 실패=" + failures + "회 violations=" + violations);
        assertThat(violations).isZero();
        // 한 번에 여러 이벤트(브로드캐스트)가 동시에 마감을 넘으면 한 step에 하나씩만 처리하므로 D + 예산보다 조금 더 기다릴 수 있다.
        assertThat(maxWait).isLessThanOrEqualTo(12 + 6 + 10);
    }
}
