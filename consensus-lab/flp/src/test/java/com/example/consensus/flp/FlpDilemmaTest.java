package com.example.consensus.flp;

import static com.example.consensus.flp.FlpScenarios.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.consensus.paxos.PaxosConfig;
import com.example.consensus.sim.SimulationResult;
import com.example.consensus.sim.StopReason;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 딜레마 실험. 제안자 A(주)와 B(예비)가 있고, B는 "A가 조용하면 타이머로 나선다".
 * <ul>
 *   <li>타이머가 없으면: A가 죽는 순간 아무도 못 나선다(활성 상실).</li>
 *   <li>타이머가 있으면: 비동기에서는 "A가 죽었다"와 "A가 느리다"를 구별할 수 없어서 B가 나서고, 적대자는 둘을 엇갈리게 한다.</li>
 *   <li>둘 다 아니면(대조군): 공정성 때문에 결국 결정된다.</li>
 * </ul>
 */
class FlpDilemmaTest {

    private static final long CRASH_TIME = 7; // A의 Prepare(5)는 acceptor에 닿았고 Promise(10)는 아직 A에 닿기 전: 첫 라운드 도중
    private static final long TAKEOVER = 30;  // 예비 B가 나서는 시각(A의 정상 결정 시각 약 20보다 늦다)
    private static final int SEEDS = 30;
    private static final long MAX_STEPS = 1_000;

    private static PaxosConfig withBackup(boolean timer) {
        PaxosConfig base = PaxosConfig.of(proposals(N1, "A", N2, "B")).withRetryTimeout(12).withBackoff(0, 0).withRetryJitter(3);
        return timer ? base.withStartOffset(N2, TAKEOVER) : base;
    }

    private static Set<com.example.consensus.sim.NodeId> passiveB(boolean timer) {
        return timer ? Set.of() : Set.of(N2);
    }

    @Test
    @DisplayName("(a) B에게 나설 타이머가 없고 A가 첫 라운드 도중 크래시하면 영원히 결정되지 않는다")
    void noTimerAndCrash() {
        SimulationResult timeOrder = runTimeOrder(source(1, withBackup(false), passiveB(false), Set.of(), CRASH_TIME), 100_000);
        AdversaryRun adversary = runAdversary(source(1, withBackup(false), passiveB(false), Set.of(), CRASH_TIME), MAX_STEPS, 1);
        SimulationResult noCrash = runTimeOrder(source(1, withBackup(false), passiveB(false), Set.of(), -1), 100_000);

        System.out.println("[FLP] (a) 타이머 없음 + A 크래시(t=" + CRASH_TIME + ") 시간순: " + timeOrder.reason() + " decided=" + timeOrder.decisions().size()
                + " steps=" + timeOrder.steps() + " (대기 이벤트가 바닥나 더 일어날 일이 없다)");
        System.out.println("[FLP] (a) 타이머 없음 + A 크래시 adversary: " + adversary.result().reason() + " decided=" + adversary.result().decisions().size()
                + " steps=" + adversary.result().steps());
        System.out.println("[FLP] (a) 대조군 크래시 없음 시간순: " + noCrash.reason() + " decided=" + noCrash.decisions().size()
                + " value=" + noCrash.decisions().get(0).value());

        assertThat(timeOrder.reason()).isEqualTo(StopReason.NO_MORE_EVENTS);
        assertThat(timeOrder.decisions()).isEmpty();
        assertThat(adversary.result().decisions()).isEmpty();
        assertThat(adversary.violated()).isFalse();
        assertThat(noCrash.decisions()).isNotEmpty();
    }

    @Test
    @DisplayName("(a') B에게 타이머가 있으면 A가 크래시해도 B가 나서서 결정한다")
    void timerRescuesCrash() {
        SimulationResult r = runTimeOrder(source(1, withBackup(true), passiveB(true), Set.of(), CRASH_TIME), 100_000);
        System.out.println("[FLP] (a') 타이머 있음 + A 크래시 시간순: " + r.reason() + " decided=" + r.decisions().size()
                + " value=" + r.decisions().get(0).value() + " 첫 결정 시각=" + r.decisions().get(0).time());
        assertThat(r.reason()).isEqualTo(StopReason.STOP_CONDITION);
        assertThat(r.decisions().get(0).value()).isEqualTo("B");
    }

    @Test
    @DisplayName("(b) 크래시 없이 타이머가 있어도 적대자가 A의 메시지를 미뤄 B를 나서게 하고 서로 끊어 결정을 막는다")
    void timerWithAdversary() {
        int timeOrderDecided = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            SimulationResult r = runTimeOrder(source(seed, withBackup(true), passiveB(true), Set.of(), -1), 3_000);
            if (!r.decisions().isEmpty() && "A".equals(r.decisions().get(0).value())) {
                timeOrderDecided++;
            }
        }

        int decided = 0, violations = 0;
        long maxWait = 0, failures = 0;
        long startNanos = System.nanoTime();
        for (long seed = 1; seed <= SEEDS; seed++) {
            AdversaryRun run = runAdversary(source(seed, withBackup(true), passiveB(true), Set.of(), -1), MAX_STEPS, seed);
            decided += run.decided() ? 1 : 0;
            violations += run.violated() ? 1 : 0;
            maxWait = Math.max(maxWait, run.adversary().maxWait());
            failures += run.adversary().failures();
        }
        long millis = (System.nanoTime() - startNanos) / 1_000_000;

        System.out.println("[FLP] (b) 타이머 있음(B는 t=" + TAKEOVER + "에 나섬), 크래시 없음, 시간순: A가 결정 " + timeOrderDecided + "/" + SEEDS);
        System.out.println("[FLP] (b) 타이머 있음, 크래시 없음, adversary decided=" + decided + "/" + SEEDS + " (maxSteps=" + MAX_STEPS
                + " 안에서) maxWait=" + maxWait + " 적대자 실패=" + failures + "회 violations=" + violations + " 소요 " + millis + "ms");

        assertThat(timeOrderDecided).isEqualTo(SEEDS);
        assertThat(violations).isZero();
        assertThat(decided).isZero();
        assertThat(maxWait).isLessThanOrEqualTo(DEADLINE + INSERT_BUDGET);
    }

    @Test
    @DisplayName("(c) 대조군: 크래시도 타이머(B의 나서기, A의 재시도)도 없으면 적대자가 있어도 공정성 때문에 결국 결정된다")
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
        System.out.println("[FLP] (c) 크래시 없음 + 타이머 없음(B 수동, A 재시도 없음) + adversary decided=" + decided + "/" + SEEDS
                + " maxWait=" + maxWait + " 가장 긴 실행 steps=" + maxSteps + " violations=" + violations
                + " (처음부터 {A}뿐이라 꺾을 갈림길이 없다)");

        assertThat(violations).isZero();
        assertThat(decided).isEqualTo(SEEDS);
    }

    @Test
    @DisplayName("(c') 예상 밖 관찰: B가 수동이어도 A의 재시도 타이머가 남아 있으면 적대자가 A 혼자의 라운드도 끊는다")
    void soloProposerWithRetryTimerIsAlsoBlocked() {
        int decided = 0, violations = 0;
        long maxWait = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            AdversaryRun run = runAdversary(source(seed, withBackup(false), passiveB(false), Set.of(), -1), MAX_STEPS, seed);
            decided += run.decided() ? 1 : 0;
            violations += run.violated() ? 1 : 0;
            maxWait = Math.max(maxWait, run.adversary().maxWait());
        }
        System.out.println("[FLP] (c') 크래시 없음 + B 수동 + A는 재시도 타이머 있음 + adversary decided=" + decided + "/" + SEEDS
                + " (maxSteps=" + MAX_STEPS + " 안에서) maxWait=" + maxWait + " violations=" + violations
                + " (갈림길이 없는데도 막힌다: 타이머가 응답보다 먼저 울리는 순서 때문)");

        assertThat(violations).isZero();
        assertThat(decided).isZero();
    }

    @Test
    @DisplayName("기준선: 확률 판정 없이 '오래된 이벤트부터'만 꺼내는 FIFO 스케줄러도 같은 설정을 막는가")
    void fifoBaseline() {
        int duelDecided = 0, soloDecided = 0, violations = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            SimulationResult duel = fifo(source(seed, duel(3)));
            SimulationResult solo = fifo(source(seed, withBackup(false), passiveB(false), Set.of(), -1));
            duelDecided += duel.decisions().isEmpty() ? 0 : 1;
            soloDecided += solo.decisions().isEmpty() ? 0 : 1;
            violations += (duel.reason() == StopReason.INVARIANT_VIOLATED ? 1 : 0) + (solo.reason() == StopReason.INVARIANT_VIOLATED ? 1 : 0);
        }
        System.out.println("[FLP] 기준선 FIFO(판정 없음, 생성 순서대로) 1편 설정 A,B jitter=3 decided=" + duelDecided + "/" + SEEDS
                + ", A 혼자(재시도 타이머 있음) decided=" + soloDecided + "/" + SEEDS + " violations=" + violations);
        assertThat(violations).isZero();
    }

    @Test
    @DisplayName("(d) 타이머가 아예 없어도(둘 다 재시도 타이머 없음) 제안자가 둘이면 적대자가 Nack 연쇄로 결정을 막는다")
    void noTimersAtAllDuel() {
        int fifoDecided = 0, advDecided = 0, violations = 0;
        long maxWait = 0, failures = 0;
        long startNanos = System.nanoTime();
        PaxosConfig config = duel(0).withStartOffset(N2, 0);
        for (long seed = 1; seed <= SEEDS; seed++) {
            fifoDecided += fifo(source(seed, config, Set.of(), Set.of(N1, N2), -1)).decisions().isEmpty() ? 0 : 1;
            AdversaryRun run = runAdversary(source(seed, config, Set.of(), Set.of(N1, N2), -1), MAX_STEPS, seed);
            advDecided += run.decided() ? 1 : 0;
            violations += run.violated() ? 1 : 0;
            maxWait = Math.max(maxWait, run.adversary().maxWait());
            failures += run.adversary().failures();
        }
        long millis = (System.nanoTime() - startNanos) / 1_000_000;
        System.out.println("[FLP] (d) 타이머 전혀 없음, 제안자 A,B: FIFO decided=" + fifoDecided + "/" + SEEDS + ", adversary decided=" + advDecided + "/" + SEEDS
                + " (maxSteps=" + MAX_STEPS + " 안에서) maxWait=" + maxWait + " 적대자 실패=" + failures + "회 violations=" + violations + " 소요 " + millis + "ms");
        assertThat(violations).isZero();
    }

    private static SimulationResult fifo(SimulationSource source) {
        return source.newBuilder()
                .scheduler((pending, view) -> {
                    var best = pending.get(0);
                    for (var e : pending) {
                        if (e.seq() < best.seq()) {
                            best = e;
                        }
                    }
                    return best;
                })
                .stopWhen(v -> !v.decisions().isEmpty()).maxSteps(MAX_STEPS).build().run();
    }
}
