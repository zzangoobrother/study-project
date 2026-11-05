package com.example.consensus.raft;

import static com.example.consensus.raft.RaftScenarios.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.consensus.sim.ReliableNetwork;
import com.example.consensus.sim.SimulationBuilder;
import com.example.consensus.sim.SimulationResult;
import com.example.consensus.sim.StopReason;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 선거 타임아웃이 고정이면 표가 갈리고, 무작위면 대부분 한 번에 끝난다.
 *
 * <p>조건(모든 변형 공통): 서버 5대, 시각 0에 동시에 시작, ReliableNetwork(1,10), 장애 없음, 하트비트 50.
 * 첫 리더가 뽑히면 멈추고, 못 뽑으면 가상 시각 {@value #CAP}에서 멈춘다.
 */
class RaftElectionTimeoutTest {

    private static final long CAP = 5_000;
    private static final int SEEDS = 1_000;

    /** 변형 하나의 집계. */
    private record Outcome(int elected, double electionsPerSeed, double timeToLeader, double failedTermRatio,
                           double avgTermsWithCandidates, int violations) {}

    private Outcome run(RaftConfig config, boolean staggeredStart) {
        int elected = 0, violations = 0;
        double elections = 0, time = 0, failedTerms = 0, termsWithCandidates = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            RaftMonitor monitor = new RaftMonitor();
            SimulationBuilder b = base(seed, RaftNode::new, config, RaftWorkload.none(), monitor, new ReliableNetwork(1, 10))
                    .stopWhen(view -> monitor.firstLeaderTime() >= 0 || view.now() >= CAP)
                    .maxSteps(1_000_000);
            if (staggeredStart) {
                // N2..N5를 시각 0에 껐다가 20씩 어긋나게 켠다. 타임아웃은 그대로 고정이지만 시작 시각이 달라 대칭이 깨진다.
                for (int i = 1; i < 5; i++) {
                    b.crashAt(0, ALL.get(i)).restartAt(20L * i, ALL.get(i));
                }
            }
            SimulationResult r = b.build().run();
            violations += r.reason() == StopReason.INVARIANT_VIOLATED ? 1 : 0;
            if (monitor.firstLeaderTime() >= 0) {
                elected++;
                time += monitor.firstLeaderTime();
            }
            elections += monitor.electionsStarted();
            failedTerms += monitor.failedTerms();
            termsWithCandidates += monitor.termsWithCandidates();
        }
        return new Outcome(elected, elections / SEEDS, elected == 0 ? Double.NaN : time / elected,
                failedTerms / termsWithCandidates, termsWithCandidates / SEEDS, violations);
    }

    private void print(String label, Outcome o) {
        System.out.println(String.format(Locale.ROOT,
                "[RAFT] 선거 타임아웃 %s (시드 1..%d, 상한 %d): 리더 선출 성공 %d/%d, 표가 갈린(후보는 있었지만 리더 없는) 임기 비율 %.1f%%, "
                        + "시드당 선거 시작 평균 %.2f회(임기 %.2f개), 첫 리더까지 평균 시각 %.1f, 위반 %d",
                label, SEEDS, CAP, o.elected(), SEEDS, 100 * o.failedTermRatio(), o.electionsPerSeed(), o.avgTermsWithCandidates(),
                o.timeToLeader(), o.violations()));
    }

    @Test
    @DisplayName("선거 타임아웃: 고정 / 좁은 범위 무작위 / 넓은 범위 무작위의 표 갈림 비교")
    void fixedVersusRandomTimeout() {
        RaftConfig base = RaftConfig.of();
        Outcome fixed = run(base.withElectionTimeout(150, 150), false);
        Outcome fixedStaggered = run(base.withElectionTimeout(150, 150), true);
        Outcome narrow = run(base.withElectionTimeout(150, 160), false);
        Outcome wide = run(base.withElectionTimeout(150, 300), false);
        print("고정 150, 전원 동시 시작", fixed);
        print("고정 150, 시작 시각만 20씩 어긋남", fixedStaggered);
        print("무작위 [150,160]", narrow);
        print("무작위 [150,300]", wide);

        assertThat(fixed.violations() + fixedStaggered.violations() + narrow.violations() + wide.violations()).isZero();
        assertThat(fixed.elected()).isZero();
        assertThat(wide.elected()).isEqualTo(SEEDS);
        assertThat(wide.failedTermRatio()).isLessThan(narrow.failedTermRatio());
    }
}
