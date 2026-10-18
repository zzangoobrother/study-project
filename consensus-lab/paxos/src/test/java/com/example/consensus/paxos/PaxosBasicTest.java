package com.example.consensus.paxos;

import static com.example.consensus.paxos.PaxosScenarios.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.consensus.sim.Decision;
import com.example.consensus.sim.LossyNetwork;
import com.example.consensus.sim.ReliableNetwork;
import com.example.consensus.sim.SeedReport;
import com.example.consensus.sim.SeedRunner;
import com.example.consensus.sim.Simulation;
import com.example.consensus.sim.SimulationResult;
import com.example.consensus.sim.StopReason;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaxosBasicTest {

    private static final PaxosConfig ONE_PROPOSER = PaxosConfig.of(proposals(N1, "A"));

    @Test
    @DisplayName("Ballot은 round를 먼저, 같으면 proposer id로 비교해 서로 다른 proposer의 ballot이 같아지지 않는다")
    void ballotOrdering() {
        assertThat(new Ballot(1, N2)).isGreaterThan(new Ballot(1, N1));
        assertThat(new Ballot(2, N1)).isGreaterThan(new Ballot(1, N3));
        assertThat(new Ballot(1, N1)).isNotEqualTo(new Ballot(1, N2));
    }

    @Test
    @DisplayName("chooseValue는 acceptedBallot이 가장 높은 Promise의 값을 고르고, 없으면 자기 값을 고른다")
    void chooseValueRule() {
        Proposer proposer = new Proposer(ONE_PROPOSER, "MINE");
        Ballot b = new Ballot(9, N1);
        var none = new PaxosMessage.Promise(b, null, null);
        var low = new PaxosMessage.Promise(b, new Ballot(1, N2), "LOW");
        var high = new PaxosMessage.Promise(b, new Ballot(3, N1), "HIGH");

        assertThat(proposer.chooseValue(List.of(none, none))).isEqualTo("MINE");
        assertThat(proposer.chooseValue(List.of(low, none, high))).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("노드 5개, proposer 1개, 신뢰 네트워크: 모든 노드가 같은 값을 결정한다")
    void allNodesDecideSameValue() {
        SeedReport report = SeedRunner.run(1, 200, seed -> base(seed, 5, PaxosNode.builder(ONE_PROPOSER), ONE_PROPOSER, new ReliableNetwork(1, 10))
                .stopWhen(PaxosScenarios::allDecided).maxSteps(10_000).build());
        printReport("정상 1: 노드 5, proposer 1, ReliableNetwork(1,10), 시드 1..200", report);

        assertThat(report.violations()).isZero();
        assertThat(report.reasonCounts().get(StopReason.STOP_CONDITION)).isEqualTo(200);

        SimulationResult one = base(1, 5, PaxosNode.builder(ONE_PROPOSER), ONE_PROPOSER, new ReliableNetwork(1, 10))
                .stopWhen(PaxosScenarios::allDecided).maxSteps(10_000).build().run();
        System.out.println("시드 1 단독 실행: 종료 시각 " + one.finalTime() + ", 보낸 메시지 " + one.sent()
                + ", 결정 " + one.decisions().stream().map(d -> d.node() + "@" + d.time() + "=" + d.value()).toList());
        assertThat(one.decisions()).hasSize(5).extracting(Decision::value).containsOnly("A");
    }

    @Test
    @DisplayName("acceptor 2개를 시작 직후 크래시해도 과반(3)이 남아 있어 결정된다")
    void decidesWithTwoCrashed() {
        SeedReport report = SeedRunner.run(1, 200, seed -> base(seed, 5, PaxosNode.builder(ONE_PROPOSER), ONE_PROPOSER, new ReliableNetwork(1, 10))
                .crashAt(0, N4).crashAt(0, N5)
                .stopWhen(v -> v.decisions().size() >= 3).maxSteps(10_000).build());
        printReport("정상 2: acceptor 2개 크래시(N4, N5), 시드 1..200", report);

        assertThat(report.violations()).isZero();
        assertThat(report.reasonCounts().get(StopReason.STOP_CONDITION)).isEqualTo(200);
    }

    @Test
    @DisplayName("acceptor 3개를 크래시하면 결정되지 않지만(과반 없음) 위반도 없다")
    void noDecisionWithThreeCrashed() {
        SeedReport report = SeedRunner.run(1, 200, seed -> base(seed, 5, PaxosNode.builder(ONE_PROPOSER), ONE_PROPOSER, new ReliableNetwork(1, 10))
                .crashAt(0, N3).crashAt(0, N4).crashAt(0, N5)
                .stopWhen(v -> v.decisions().size() >= 1).maxSteps(2_000).build());
        printReport("정상 3: acceptor 3개 크래시(N3~N5), 시드 1..200", report);

        assertThat(report.violations()).isZero();
        assertThat(report.reasonCounts().getOrDefault(StopReason.STOP_CONDITION, 0)).isZero();
    }

    @Test
    @DisplayName("proposer 3개 + 유실 네트워크 + 크래시/재시작을 섞은 시드 10,000개에서 위반이 없다")
    void contentionWithFaultsKeepsSafety() {
        PaxosConfig config = PaxosConfig.of(proposals(N1, "A", N2, "B", N3, "C"))
                .withStartDelay(0, 30).withRetryTimeout(60).withBackoff(5, 30);
        LongFactory factory = seed -> {
            Random r = new Random(seed * 31 + 7);
            double dropRate = 0.1 + 0.1 * r.nextDouble();
            var sb = base(seed, 5, PaxosNode.builder(config), config, new LossyNetwork(dropRate, 1, 20));
            // 서로 다른 노드 2~3개를 임의 시각에 크래시하고 한참 뒤 재시작한다. proposer도 포함될 수 있어
            // "Accept를 일부만 보내고 죽은 proposer를 다른 proposer가 이어받는" 상황이 자연스럽게 섞인다.
            int crashes = 2 + r.nextInt(2);
            int[] victims = r.ints(1, 6).distinct().limit(crashes).toArray();
            for (int v : victims) {
                long crashAt = 3 + r.nextInt(80);
                sb.crashAt(crashAt, new com.example.consensus.sim.NodeId(v));
                sb.restartAt(crashAt + 20 + r.nextInt(150), new com.example.consensus.sim.NodeId(v));
            }
            return sb.maxSteps(5_000).build();
        };

        SeedReport report = SeedRunner.run(1, 10_000, factory::apply);
        DecisionStats stats = measure(1, 10_000, factory::apply);
        printReport("정상 4: proposer 3, LossyNetwork(0.1~0.2, 1~20), 크래시 2~3개, 시드 1..10000", report);
        System.out.println(stats);

        assertThat(report.violations()).isZero();
        assertThat(stats.decidedRuns()).isGreaterThan(stats.runs() * 9 / 10);
    }

    @FunctionalInterface
    private interface LongFactory {
        Simulation apply(long seed);
    }
}
