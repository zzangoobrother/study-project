package com.example.consensus.paxos;

import static com.example.consensus.paxos.PaxosScenarios.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.consensus.sim.ReliableNetwork;
import com.example.consensus.sim.SeedReport;
import com.example.consensus.sim.SeedRunner;
import com.example.consensus.sim.Simulation;
import com.example.consensus.sim.SimulationResult;
import com.example.consensus.sim.StopReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Paxos는 안전성(safety)만 보장하고 활성(liveness)은 보장하지 않는다. 두 proposer가 서로의 라운드를
 * 끊어 내면 아무도 Accept 단계를 끝내지 못하는 livelock이 생긴다.
 */
class PaxosLivelockTest {

    private static final int MAX_STEPS = 3_000;

    private static Simulation symmetric(long seed, PaxosConfig config) {
        // 지연이 5로 고정이면 모든 메시지 왕복이 결정적이다. 한 라운드가 acceptor에서 끝나려면 Prepare(5) + Promise(5) + Accept(5) = 15가
        // 필요한데 retryTimeout은 12다. N2가 3 늦게 시작하면, 한쪽의 Accept가 도착하기 전에 다른 쪽의 더 높은 Prepare가
        // 먼저 도착하는 일이 매 라운드 반복된다. 값이 정해지는 일 없이 round만 올라간다.
        return base(seed, 5, PaxosNode.builder(config), config, new ReliableNetwork(5, 5))
                .stopWhen(v -> !v.decisions().isEmpty()).maxSteps(MAX_STEPS).build();
    }

    @Test
    @DisplayName("proposer 2개가 대칭 조건에서 backoff 없이 경쟁하면 결정하지 못하고 round만 계속 오른다")
    void symmetricDuelNeverDecides() {
        PaxosConfig config = PaxosConfig.of(proposals(N1, "A", N2, "B"))
                .withRetryTimeout(12).withStartOffset(N2, 3).withBackoff(0, 0);

        SimulationResult result = symmetric(1, config).run();
        int maxRound = maxPrepareRound(result);
        System.out.println("[livelock] 종료 " + result.reason() + ", 시각 " + result.finalTime() + ", 결정 " + result.decisions().size()
                + "개, 보낸 메시지 " + result.sent() + ", 마지막 Prepare의 최대 round " + maxRound);

        assertThat(result.reason()).isEqualTo(StopReason.MAX_STEPS);
        assertThat(result.decisions()).isEmpty();
        assertThat(maxRound).isGreaterThan(50);

        // 시드가 달라도 결과는 같다(난수를 전혀 쓰지 않는 결정적 livelock).
        SeedReport report = SeedRunner.run(1, 100, seed -> symmetric(seed, config));
        printReport("livelock: proposer 2, ReliableNetwork(5,5), retryTimeout=12, N2 시작 +3, backoff=0, 시드 1..100", report);
        assertThat(report.violations()).isZero();
        assertThat(report.reasonCounts().get(StopReason.MAX_STEPS)).isEqualTo(100);

        writeTrace("livelock.mmd", result, 80);
    }

    @Test
    @DisplayName("같은 네트워크라도 proposer가 하나이거나 retryTimeout이 충분히 길면 결정된다")
    void sameNetworkDecidesWithoutContention() {
        PaxosConfig solo = PaxosConfig.of(proposals(N1, "A")).withRetryTimeout(12);
        PaxosConfig patient = PaxosConfig.of(proposals(N1, "A", N2, "B")).withRetryTimeout(100).withStartOffset(N2, 3);

        assertThat(symmetric(1, solo).run().reason()).isEqualTo(StopReason.STOP_CONDITION);
        assertThat(symmetric(1, patient).run().reason()).isEqualTo(StopReason.STOP_CONDITION);
    }

    @Test
    @DisplayName("proposer 2개 시드 1,000개: 무작위 backoff가 backoff=0보다 결정률이 의미 있게 높다")
    void randomBackoffImprovesDecisionRate() {
        // 지연이 3~10으로 흔들리고 retryTimeout(12)이 왕복보다 짧은, 경쟁이 잦은 조건.
        PaxosConfig noBackoff = PaxosConfig.of(proposals(N1, "A", N2, "B"))
                .withStartDelay(0, 3).withRetryTimeout(12).withBackoff(0, 0);
        PaxosConfig randomBackoff = noBackoff.withBackoff(10, 60);

        DecisionStats zero = measure(1, 1_000, seed -> duel(seed, noBackoff));
        DecisionStats random = measure(1, 1_000, seed -> duel(seed, randomBackoff));
        printReport("backoff=0, 시드 1..1000", SeedRunner.run(1, 1_000, seed -> duel(seed, noBackoff)));
        System.out.println(zero);
        printReport("backoff=[10,60] 무작위, 시드 1..1000", SeedRunner.run(1, 1_000, seed -> duel(seed, randomBackoff)));
        System.out.println(random);

        assertThat(zero.violations()).isZero();
        assertThat(random.violations()).isZero();
        assertThat(random.decidedRuns()).isGreaterThan(zero.decidedRuns() + 100);
    }

    @Test
    @DisplayName("test 7과 같은 결정적 livelock 조건에서 retryJitter를 주면 결정률이 크게 오른다")
    void retryJitterBreaksDeterministicLivelock() {
        PaxosConfig base = PaxosConfig.of(proposals(N1, "A", N2, "B"))
                .withRetryTimeout(12).withStartOffset(N2, 3).withBackoff(0, 0);
        DecisionStats none = null;
        DecisionStats chosen = null;
        for (long jitter : new long[] {0, 3, 8, 15, 30}) {
            PaxosConfig config = base.withRetryJitter(jitter);
            SeedReport report = SeedRunner.run(1, 1_000, seed -> symmetric(seed, config));
            DecisionStats stats = measure(1, 1_000, seed -> symmetric(seed, config));
            printReport("retryJitter=" + jitter + " (ReliableNetwork(5,5), retryTimeout=12, N2 +3, backoff=0, 시드 1..1000)", report);
            System.out.println(stats);
            assertThat(stats.violations()).isZero();
            if (jitter == 0) {
                none = stats;
            }
            if (jitter == 15) {
                chosen = stats;
            }
        }
        assertThat(none.decidedRuns()).isZero();
        assertThat(chosen.decidedRuns()).isGreaterThan(900);
    }

    private static Simulation duel(long seed, PaxosConfig config) {
        return base(seed, 5, PaxosNode.builder(config), config, new ReliableNetwork(3, 10))
                .stopWhen(v -> !v.decisions().isEmpty()).maxSteps(1_500).build();
    }
}
