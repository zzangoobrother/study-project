package com.example.consensus.flp;

import static com.example.consensus.flp.FlpScenarios.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.consensus.sim.SimulationResult;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 보조 실험. 5장 본문에는 쓰지 않지만 적대자 자체를 검증하는 데 의미가 있어 남겨 둔다.
 * 타이머가 있는 설정은 FIFO만으로도 막히므로(TimerNotGuaranteeTest), 여기서 "적대자가 막았다"는 결과는 갈림길 판정의 증거가 아니다.
 * 이 클래스는 공정성 한도, 안전성, 보조정리 2 끼워넣기 경로가 동작하는지 확인하는 용도다.
 */
class AuxiliaryExperimentsTest {

    private static final int SEEDS = 50;
    private static final long MAX_STEPS = 1_000;

    @Test
    @DisplayName("1편 설정을 적대자로 돌려도 결정되지 않고, 공정성과 안전성은 지켜진다")
    void duelWithAdversary() {
        int decided = 0, violations = 0;
        long maxWait = 0, maxAge = 0, failures = 0, inserts = 0, probeRuns = 0;
        long startNanos = System.nanoTime();
        for (long seed = 1; seed <= SEEDS; seed++) {
            AdversaryRun run = runAdversary(source(seed, duel(3)), MAX_STEPS, seed);
            decided += run.decided() ? 1 : 0;
            violations += run.violated() ? 1 : 0;
            maxWait = Math.max(maxWait, run.adversary().maxWait());
            maxAge = Math.max(maxAge, run.adversary().maxObservedAge());
            failures += run.adversary().failures();
            inserts += run.adversary().inserts();
            probeRuns += run.adversary().stats().probeRuns();
        }
        long millis = (System.nanoTime() - startNanos) / 1_000_000;
        System.out.println("[FLP] 보조 1편 설정 adversary decided=" + decided + "/" + SEEDS + " (maxSteps=" + MAX_STEPS + " 안에서) violations="
                + violations + " maxWait=" + maxWait + " pending 최장 나이=" + maxAge + " 실패=" + failures + " 끼워넣기=" + inserts
                + " 프로브 실행=" + probeRuns + " 소요 " + millis + "ms");
        assertThat(violations).isZero();
        assertThat(decided).isZero();
        assertThat(maxWait).isLessThanOrEqualTo(DEADLINE + INSERT_BUDGET);
    }

    @Test
    @DisplayName("마감을 짧게(D=12) 줘서 보조정리 2의 끼워 넣기가 실제로 일어나게 해도 안전성이 지켜지고 대기가 묶인다")
    void shortDeadlineExercisesLemmaTwo() {
        int decided = 0, violations = 0, seeds = 20;
        long maxWait = 0, failures = 0, inserts = 0;
        for (long seed = 1; seed <= seeds; seed++) {
            AdversaryRun run = runAdversary(source(seed, duel(3)), MAX_STEPS, seed, 12, 6);
            decided += run.decided() ? 1 : 0;
            violations += run.violated() ? 1 : 0;
            maxWait = Math.max(maxWait, run.adversary().maxWait());
            failures += run.adversary().failures();
            inserts += run.adversary().inserts();
        }
        System.out.println("[FLP] 보조 D=12 예산=6 decided=" + decided + "/" + seeds + " maxWait=" + maxWait + " 보조정리2 끼워넣기=" + inserts
                + "회 적대자 실패=" + failures + "회 violations=" + violations);
        assertThat(violations).isZero();
        // 한 번에 여러 이벤트(브로드캐스트)가 동시에 마감을 넘으면 한 step에 하나씩만 처리하므로 D + 예산보다 조금 더 기다릴 수 있다.
        assertThat(maxWait).isLessThanOrEqualTo(12 + 6 + 10);
    }

    @Test
    @DisplayName("타이머 있는 B + 크래시 없음 + 적대자: 시간순에서는 A가 결정하고 적대자에서는 결정되지 않는다")
    void timerBackupWithAdversary() {
        int timeOrderDecided = 0, decided = 0, violations = 0, seeds = 30;
        long maxWait = 0;
        for (long seed = 1; seed <= seeds; seed++) {
            SimulationResult r = runTimeOrder(source(seed, withBackup(true), passiveB(true), Set.of(), -1), 3_000);
            timeOrderDecided += !r.decisions().isEmpty() && "A".equals(r.decisions().get(0).value()) ? 1 : 0;
            AdversaryRun run = runAdversary(source(seed, withBackup(true), passiveB(true), Set.of(), -1), MAX_STEPS, seed);
            decided += run.decided() ? 1 : 0;
            violations += run.violated() ? 1 : 0;
            maxWait = Math.max(maxWait, run.adversary().maxWait());
        }
        System.out.println("[FLP] 보조 (b) 타이머 있음, 크래시 없음: 시간순 A 결정 " + timeOrderDecided + "/" + seeds + ", adversary decided=" + decided
                + "/" + seeds + " maxWait=" + maxWait + " violations=" + violations);
        assertThat(violations).isZero();
        assertThat(timeOrderDecided).isEqualTo(seeds);
        assertThat(decided).isZero();
    }

    @Test
    @DisplayName("(c') 적대자 버전: B가 수동이고 A만 재시도 타이머가 있어도 적대자가 막는다(FIFO로도 막히는 경우)")
    void soloProposerWithRetryTimerAdversary() {
        int decided = 0, violations = 0, seeds = 10;
        for (long seed = 1; seed <= seeds; seed++) {
            AdversaryRun run = runAdversary(source(seed, withBackup(false), passiveB(false), Set.of(), -1), MAX_STEPS, seed);
            decided += run.decided() ? 1 : 0;
            violations += run.violated() ? 1 : 0;
        }
        System.out.println("[FLP] 보조 (c') 적대자 버전 B 수동 + A 재시도 타이머 decided=" + decided + "/" + seeds + " violations=" + violations);
        assertThat(violations).isZero();
        assertThat(decided).isZero();
    }
}
