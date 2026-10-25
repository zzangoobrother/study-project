package com.example.consensus.flp;

import static com.example.consensus.flp.FlpScenarios.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.consensus.sim.SimulationResult;
import com.example.consensus.sim.StopReason;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 5장 2단계. 딜레마. 제안자 A(주)와 B(예비)가 있고, B는 "A가 조용하면 타이머로 나선다".
 * <ul>
 *   <li>(a) 타이머가 없으면: A가 죽는 순간 아무도 못 나서서 멈춘다.</li>
 *   <li>(a') 타이머가 있으면: A가 죽어도 B가 나서서 결정한다.</li>
 *   <li>(c') 하지만 타이머가 있으면 시각을 무시한 순서만으로도 멈춘다(크래시가 없어도).</li>
 *   <li>(c) 타이머가 전혀 없고 크래시도 없으면 공정한 스케줄에서 결국 결정된다(대조군).</li>
 * </ul>
 */
class FlpDilemmaTest {

    private static final int SEEDS = 30;
    private static final long MAX_STEPS = 1_000;

    @Test
    @DisplayName("(a) B에게 나설 타이머가 없고 A가 첫 라운드 도중 크래시하면 영원히 결정되지 않는다")
    void noTimerAndCrash() {
        SimulationResult timeOrder = runTimeOrder(source(1, withBackup(false), passiveB(false), Set.of(), CRASH_TIME), 100_000);
        SimulationResult noCrash = runTimeOrder(source(1, withBackup(false), passiveB(false), Set.of(), -1), 100_000);

        System.out.println("[FLP] 2단계 (a) 타이머 없음 + A 크래시(t=" + CRASH_TIME + ") 시간순: " + timeOrder.reason() + " decided="
                + timeOrder.decisions().size() + " steps=" + timeOrder.steps() + " (대기 이벤트가 바닥나 더 일어날 일이 없다)");
        System.out.println("[FLP] 2단계 (a) 대조군 크래시 없음 시간순: " + noCrash.reason() + " decided=" + noCrash.decisions().size()
                + " value=" + noCrash.decisions().get(0).value());

        assertThat(timeOrder.reason()).isEqualTo(StopReason.NO_MORE_EVENTS);
        assertThat(timeOrder.decisions()).isEmpty();
        assertThat(noCrash.decisions()).isNotEmpty();
    }

    @Test
    @DisplayName("(a') B에게 타이머가 있으면 A가 크래시해도 B가 나서서 결정한다")
    void timerRescuesCrash() {
        SimulationResult r = runTimeOrder(source(1, withBackup(true), passiveB(true), Set.of(), CRASH_TIME), 100_000);
        System.out.println("[FLP] 2단계 (a') 타이머 있음 + A 크래시 시간순: " + r.reason() + " decided=" + r.decisions().size()
                + " value=" + r.decisions().get(0).value() + " 첫 결정 시각=" + r.decisions().get(0).time());
        assertThat(r.reason()).isEqualTo(StopReason.STOP_CONDITION);
        assertThat(r.decisions().get(0).value()).isEqualTo("B");
    }

    @Test
    @DisplayName("(c') 크래시가 없어도 타이머가 있으면 시각을 무시한 순서(FIFO)만으로 결정이 막힌다")
    void timerWithoutCrashStillBlockedByFifo() {
        int timeOrderDecided = 0, fifoDecided = 0, violations = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            timeOrderDecided += runTimeOrder(source(seed, withBackup(true), passiveB(true), Set.of(), -1), 3_000).decisions().isEmpty() ? 0 : 1;
            SimulationResult fifo = runFifo(source(seed, withBackup(true), passiveB(true), Set.of(), -1), MAX_STEPS);
            fifoDecided += fifo.decisions().isEmpty() ? 0 : 1;
            violations += fifo.reason() == StopReason.INVARIANT_VIOLATED ? 1 : 0;
        }
        System.out.println("[FLP] 2단계 (c') 타이머 있음(A 재시도, B는 t=" + TAKEOVER + "에 나섬), 크래시 없음: 시간순 decided=" + timeOrderDecided
                + "/" + SEEDS + ", FIFO decided=" + fifoDecided + "/" + SEEDS + " (maxSteps=" + MAX_STEPS + " 안에서) violations=" + violations);
        assertThat(violations).isZero();
        assertThat(timeOrderDecided).isEqualTo(SEEDS);
        assertThat(fifoDecided).isZero();
    }

    @Test
    @DisplayName("(c) 대조군: 크래시도 타이머(B의 나서기, A의 재시도)도 없으면 적대자가 있어도 결국 결정된다")
    void controlDecidesDespiteAdversary() {
        int decided = 0, violations = 0;
        long maxWait = 0, maxSteps = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            AdversaryRun run = runAdversary(source(seed, withBackup(false), passiveB(false), Set.of(N1), -1), MAX_STEPS, seed);
            decided += run.decided() ? 1 : 0;
            violations += run.violated() ? 1 : 0;
            maxWait = Math.max(maxWait, run.adversary().maxWait());
            maxSteps = Math.max(maxSteps, run.result().steps());
        }
        System.out.println("[FLP] 2단계 (c) 크래시 없음 + 타이머 없음(B 수동, A 재시도 없음) + adversary decided=" + decided + "/" + SEEDS
                + " maxWait=" + maxWait + " 가장 긴 실행 steps=" + maxSteps + " violations=" + violations
                + " (처음부터 {A}뿐이라 꺾을 갈림길이 없다)");
        assertThat(violations).isZero();
        assertThat(decided).isEqualTo(SEEDS);
    }
}
