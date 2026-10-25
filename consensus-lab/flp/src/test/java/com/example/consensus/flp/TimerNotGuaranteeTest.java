package com.example.consensus.flp;

import static com.example.consensus.flp.FlpScenarios.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.consensus.sim.SimulationResult;
import com.example.consensus.sim.StopReason;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 5장 1단계. 비동기에서 타이머는 보장이 아니다.
 * 같은 1편 설정(지연 5 고정, retryTimeout 12, retryJitter 3)이 시간순에서는 전부 결정되지만,
 * 시각을 무시한 FIFO 순서(타이머가 응답보다 먼저 울리는 것도 합법)에서는 적대자 없이도 결정되지 않는다.
 */
class TimerNotGuaranteeTest {

    private static final int FIFO_SEEDS = 30;
    private static final long FIFO_MAX_STEPS = 1_000;

    @Test
    @DisplayName("시간순 스케줄러: 1편 설정(jitter 3)은 시드 1,000개 모두 결정된다")
    void timeOrderDecidesEverySeed() {
        int decided = 0, violations = 0;
        for (long seed = 1; seed <= 1_000; seed++) {
            SimulationResult r = runTimeOrder(source(seed, duel(3)), 3_000);
            decided += r.decisions().isEmpty() ? 0 : 1;
            violations += r.reason() == StopReason.INVARIANT_VIOLATED ? 1 : 0;
        }
        System.out.println("[FLP] 1단계 시간순 jitter=3 decided=" + decided + "/1000 violations=" + violations);
        assertThat(violations).isZero();
        assertThat(decided).isEqualTo(1_000);
    }

    @Test
    @DisplayName("FIFO 스케줄러(적대자 아님): 같은 설정이 maxSteps 안에서 결정되지 않는다")
    void fifoBlocksTimerBasedPaxos() {
        int duelDecided = 0, soloDecided = 0, violations = 0;
        for (long seed = 1; seed <= FIFO_SEEDS; seed++) {
            SimulationResult duel = runFifo(source(seed, duel(3)), FIFO_MAX_STEPS);
            SimulationResult solo = runFifo(source(seed, withBackup(false), passiveB(false), Set.of(), -1), FIFO_MAX_STEPS);
            duelDecided += duel.decisions().isEmpty() ? 0 : 1;
            soloDecided += solo.decisions().isEmpty() ? 0 : 1;
            violations += (duel.reason() == StopReason.INVARIANT_VIOLATED ? 1 : 0) + (solo.reason() == StopReason.INVARIANT_VIOLATED ? 1 : 0);
        }
        System.out.println("[FLP] 1단계 FIFO(시각 무시, 생성 순서대로) 제안자 A,B jitter=3 decided=" + duelDecided + "/" + FIFO_SEEDS
                + " (maxSteps=" + FIFO_MAX_STEPS + " 안에서), 제안자 A 혼자 decided=" + soloDecided + "/" + FIFO_SEEDS + " violations=" + violations);
        assertThat(violations).isZero();
        assertThat(duelDecided).isZero();
        assertThat(soloDecided).isZero();
    }
}
