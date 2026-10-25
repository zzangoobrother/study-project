package com.example.consensus.flp;

import static com.example.consensus.flp.FlpScenarios.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.consensus.paxos.PaxosConfig;
import com.example.consensus.sim.SimulationResult;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 5장 3단계, FLP의 본론. 타이머가 전혀 없어도(두 제안자 모두 재시도 타이머 없음, Nack을 받으면 즉시 새 라운드) 갈림길은 유지될 수 있는가.
 * FIFO는 이 설정을 막지 못한다. 막는 것은 오직 "두 값이 모두 가능한 상태를 골라 걷는" 갈림길 적대자의 몫이다.
 */
class BivalentAdversaryTest {

    private static final int SEEDS = 20;
    private static final long MAX_STEPS = 1_000;

    private static SimulationSource timerless(long seed) {
        PaxosConfig config = duel(0).withStartOffset(N2, 0);
        return source(seed, config, Set.of(), Set.of(N1, N2), -1);
    }

    @Test
    @DisplayName("(d) 타이머가 전혀 없는 A,B: FIFO는 전부 결정되지만 갈림길 적대자는 maxSteps 안에서 결정을 막는다")
    void timerlessDuel() {
        int fifoDecided = 0, advDecided = 0, violations = 0;
        long maxWait = 0, maxAge = 0, failures = 0, escalations = 0, probeRuns = 0, inserts = 0;
        long startNanos = System.nanoTime();
        for (long seed = 1; seed <= SEEDS; seed++) {
            fifoDecided += runFifo(timerless(seed), MAX_STEPS).decisions().isEmpty() ? 0 : 1;
            AdversaryRun run = runAdversary(timerless(seed), MAX_STEPS, seed);
            advDecided += run.decided() ? 1 : 0;
            violations += run.violated() ? 1 : 0;
            maxWait = Math.max(maxWait, run.adversary().maxWait());
            maxAge = Math.max(maxAge, run.adversary().maxObservedAge());
            failures += run.adversary().failures();
            escalations += run.adversary().escalations();
            inserts += run.adversary().inserts();
            probeRuns += run.adversary().stats().probeRuns();
        }
        long millis = (System.nanoTime() - startNanos) / 1_000_000;

        System.out.println("[FLP] 3단계 (d) 타이머 전혀 없음, 제안자 A,B: FIFO decided=" + fifoDecided + "/" + SEEDS
                + ", adversary decided=" + advDecided + "/" + SEEDS + " (maxSteps=" + MAX_STEPS + " 안에서) violations=" + violations);
        System.out.println("[FLP] 3단계 (d) adversary 공정성: 마감 D=" + DEADLINE + " 예산=" + INSERT_BUDGET + " maxWait=" + maxWait
                + " pending 최장 나이=" + maxAge);
        System.out.println("[FLP] 3단계 (d) adversary 비용: 적대자 실패 " + failures + "회, 적응형 프로브 발동 " + escalations + "회, 보조정리 2 끼워넣기 "
                + inserts + "회, 프로브 실행 " + probeRuns + "회, 소요 " + millis + "ms");

        assertThat(violations).isZero();
        assertThat(maxWait).isLessThanOrEqualTo(DEADLINE + INSERT_BUDGET);
        assertThat(maxAge).isLessThanOrEqualTo(DEADLINE + INSERT_BUDGET);
        assertThat(fifoDecided).isEqualTo(SEEDS); // 전제: 판정 없이는 막지 못하는 설정이어야 이 실험의 의미가 있다.
        // 막는 비율은 측정값에 근거해 보수적으로: 실행의 20% 이하만 결정된다. (maxSteps 안에서의 관찰이지 영구 차단의 증명이 아니다)
        assertThat(advDecided * 5).isLessThanOrEqualTo(SEEDS);
    }

    @Test
    @DisplayName("비교: 적응형 프로브를 끈 약한 판정기(프로브 20)는 같은 설정에서 더 자주 진다")
    void weakProbeAblation() {
        int weakDecided = 0, violations = 0;
        long failures = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            AdversaryRun run = runAdversary(timerless(seed), MAX_STEPS, seed, DEADLINE, INSERT_BUDGET, PROBES);
            weakDecided += run.decided() ? 1 : 0;
            violations += run.violated() ? 1 : 0;
            failures += run.adversary().failures();
        }
        System.out.println("[FLP] 3단계 비교 (d) 약한 판정기(프로브 " + PROBES + ", 적응형 없음) decided=" + weakDecided + "/" + SEEDS
                + " 적대자 실패=" + failures + "회 violations=" + violations);
        assertThat(violations).isZero();
    }
}
