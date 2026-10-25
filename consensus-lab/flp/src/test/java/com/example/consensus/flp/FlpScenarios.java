package com.example.consensus.flp;

import com.example.consensus.paxos.PaxosConfig;
import com.example.consensus.paxos.PaxosInvariants;
import com.example.consensus.paxos.PaxosNode;
import com.example.consensus.sim.Invariants;
import com.example.consensus.sim.Node;
import com.example.consensus.sim.NodeId;
import com.example.consensus.sim.ReliableNetwork;
import com.example.consensus.sim.Simulation;
import com.example.consensus.sim.SimulationBuilder;
import com.example.consensus.sim.SimulationResult;
import com.example.consensus.sim.StopReason;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/** FLP 실험 테스트들이 공유하는 Paxos 클러스터 구성. 1편 PaxosLivelockTest의 설정(지연 5 고정, retryTimeout 12)을 따른다. */
final class FlpScenarios {

    static final NodeId N1 = new NodeId(1);
    static final NodeId N2 = new NodeId(2);
    static final int NODES = 5;

    /** 적대자 기본 파라미터. 테스트 시간과 정확도의 타협이다(측정값은 보고서 참조). */
    static final int PROBES = 20;
    static final int PROBE_MAX_STEPS = 300;
    static final int DEADLINE = 40;
    static final int INSERT_BUDGET = 10;
    static final int MAX_CANDIDATES = 12;

    private FlpScenarios() {}

    static Map<NodeId, Object> proposals(Object... idValuePairs) {
        Map<NodeId, Object> map = new TreeMap<>();
        for (int i = 0; i < idValuePairs.length; i += 2) {
            map.put((NodeId) idValuePairs[i], idValuePairs[i + 1]);
        }
        return map;
    }

    /** 1편 livelock 설정: A=N1, B=N2, retryTimeout 12, N2는 3 늦게 시작, backoff 없음. 지터만 바꿔 가며 쓴다. */
    static PaxosConfig duel(long retryJitter) {
        return PaxosConfig.of(proposals(N1, "A", N2, "B"))
                .withRetryTimeout(12).withStartOffset(N2, 3).withBackoff(0, 0).withRetryJitter(retryJitter);
    }

    /**
     * 시뮬레이션 공장. passive에 든 노드는 {@link PassiveProposer}(나설 타이머 없음), oneShot에 든 노드는
     * {@link OneShotProposer}(재시도 타이머 없음)로 만든다. crashN1At이 0 이상이면 N1을 그 시각에 크래시시킨다.
     */
    static SimulationSource source(long seed, PaxosConfig config, Set<NodeId> passive, Set<NodeId> oneShot, long crashN1At) {
        Function<NodeId, Node> normal = PaxosNode.builder(config).factory();
        Function<NodeId, Node> passiveFactory = PaxosNode.builder(config).proposer(PassiveProposer::new).factory();
        Function<NodeId, Node> oneShotFactory = PaxosNode.builder(config).proposer(OneShotProposer::new).factory();
        Function<NodeId, Node> factory = id -> passive.contains(id) ? passiveFactory.apply(id)
                : oneShot.contains(id) ? oneShotFactory.apply(id) : normal.apply(id);
        return () -> {
            SimulationBuilder b = Simulation.builder()
                    .seed(seed)
                    .nodes(NODES, factory)
                    .network(new ReliableNetwork(5, 5))
                    .invariant(Invariants.agreement())
                    .invariant(PaxosInvariants.validity(Set.copyOf(config.proposals().values())));
            if (crashN1At >= 0) {
                b.crashAt(crashN1At, N1);
            }
            return b;
        };
    }

    static SimulationSource source(long seed, PaxosConfig config) {
        return source(seed, config, Set.of(), Set.of(), -1);
    }

    static SimulationResult runTimeOrder(SimulationSource source, long maxSteps) {
        return source.newBuilder().stopWhen(v -> !v.decisions().isEmpty()).maxSteps(maxSteps).build().run();
    }

    /** 적대자 실행 결과와 통계. */
    record AdversaryRun(SimulationResult result, AdversarialScheduler adversary) {
        boolean decided() {
            return !result.decisions().isEmpty();
        }

        boolean violated() {
            return result.reason() == StopReason.INVARIANT_VIOLATED;
        }
    }

    static AdversaryRun runAdversary(SimulationSource source, long maxSteps, long probeSeed) {
        return runAdversary(source, maxSteps, probeSeed, DEADLINE, INSERT_BUDGET);
    }

    /** 프로브 시드를 시드마다 바꿔 준다. 적대자는 시각을 보지 않으므로, 시드 차이는 프로브 표집에서만 생긴다. */
    static AdversaryRun runAdversary(SimulationSource source, long maxSteps, long probeSeed, int deadline, int insertBudget) {
        BivalenceProbe probe = new BivalenceProbe(source, PROBES, PROBE_MAX_STEPS, probeSeed);
        AdversarialScheduler adversary = new AdversarialScheduler(probe, deadline, insertBudget, MAX_CANDIDATES);
        SimulationResult result = source.newBuilder()
                .scheduler(adversary)
                .stopWhen(v -> !v.decisions().isEmpty())
                .maxSteps(maxSteps)
                .build().run();
        return new AdversaryRun(result, adversary);
    }
}
