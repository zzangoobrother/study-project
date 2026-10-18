package com.example.consensus.paxos;

import static com.example.consensus.paxos.PaxosScenarios.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.consensus.paxos.broken.IgnoreAcceptedValueProposer;
import com.example.consensus.paxos.broken.SkipPrepareProposer;
import com.example.consensus.paxos.broken.VolatileAcceptor;
import com.example.consensus.sim.LossyNetwork;
import com.example.consensus.sim.SeedReport;
import com.example.consensus.sim.SeedRunner;
import com.example.consensus.sim.Simulation;
import com.example.consensus.sim.SimulationResult;
import com.example.consensus.sim.StopReason;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.LongFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 같은 조건에서 정상 구현은 위반 0건, 틀린 변형은 위반이 나옴을 나란히 보인다.
 *
 * <p>공통 시나리오 "늦게 깨어난 proposer":
 * <ol>
 *   <li>N1이 시작하자마자 A를 제안한다. N2(proposer, 값 B)는 시각 0에 죽어 있다.</li>
 *   <li>A가 결정된 뒤(시각 100) 전 노드가 한꺼번에 크래시하고 150에 재시작한다.</li>
 *   <li>N2는 150에 재시작해 B를 제안한다. 이때 Accept된 값을 기억하는 것은 Storage뿐이다.</li>
 * </ol>
 * 정상 구현은 Storage 덕분에 N2가 A를 이어받는다.
 */
class PaxosCounterexampleTest {

    private static final int FROM = 1;
    private static final int TO = 1_000;

    private static final PaxosConfig CONFIG = PaxosConfig.of(proposals(N1, "A", N2, "B"))
            .withStartDelay(0, 5).withRetryTimeout(60).withBackoff(5, 20);

    private static LongFunction<Simulation> lateProposer(PaxosNode.Builder node) {
        return seed -> {
            var sb = base(seed, 5, node, CONFIG, new LossyNetwork(0.1, 1, 10)).crashAt(0, N2);
            for (var id : java.util.List.of(N1, N2, N3, N4, N5)) {
                sb.crashAt(100, id).restartAt(150, id);
            }
            return sb.maxSteps(5_000).build();
        };
    }

    private void compare(String variantName, Function<PaxosConfig, PaxosNode.Builder> brokenBuilder) {
        SeedReport normal = SeedRunner.run(FROM, TO, lateProposer(PaxosNode.builder(CONFIG)));
        SeedReport broken = SeedRunner.run(FROM, TO, lateProposer(brokenBuilder.apply(CONFIG)));
        printReport("정상 (시드 " + FROM + ".." + TO + ")", normal);
        printReport(variantName + " (시드 " + FROM + ".." + TO + ")", broken);
        System.out.println("비교 " + variantName + ": 정상 위반 " + normal.violations() + "건 vs 변형 위반 " + broken.violations() + "건");

        assertThat(normal.violations()).isZero();
        assertThat(broken.violations()).isGreaterThanOrEqualTo(1);

        long seed = broken.firstViolationSeeds().get(0);
        SimulationResult result = lateProposer(brokenBuilder.apply(CONFIG)).apply(seed).run();
        assertThat(result.reason()).isEqualTo(StopReason.INVARIANT_VIOLATED);
        // 위반 시점에 실행이 멈추므로 trace 전체가 "위반 직전까지의 흐름"이다.
        Path file = writeTrace(variantName + "-seed" + seed + ".mmd", result, 400);
        System.out.println("위반 시드 " + seed + ": " + result.violation().message() + " -> " + file);
    }

    @Test
    @DisplayName("IgnoreAcceptedValueProposer: Promise의 accepted 값을 무시하면 이미 선택된 값이 뒤집힌다")
    void ignoreAcceptedValue() {
        compare("IgnoreAcceptedValueProposer", c -> PaxosNode.builder(c).proposer(IgnoreAcceptedValueProposer::new));
    }

    @Test
    @DisplayName("SkipPrepareProposer: 1단계를 건너뛰면 이미 선택된 값이 뒤집힌다")
    void skipPrepare() {
        compare("SkipPrepareProposer", c -> PaxosNode.builder(c).proposer(SkipPrepareProposer::new));
    }

    @Test
    @DisplayName("VolatileAcceptor: 재시작하면 약속과 accept를 잊어 이미 선택된 값이 뒤집힌다")
    void volatileAcceptor() {
        compare("VolatileAcceptor", c -> PaxosNode.builder(c).acceptor(VolatileAcceptor::new));
    }
}
