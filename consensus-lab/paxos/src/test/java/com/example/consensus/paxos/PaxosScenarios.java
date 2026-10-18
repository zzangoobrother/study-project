package com.example.consensus.paxos;

import com.example.consensus.sim.Decision;
import com.example.consensus.sim.Invariants;
import com.example.consensus.sim.NetworkPolicy;
import com.example.consensus.sim.NodeId;
import com.example.consensus.sim.SeedReport;
import com.example.consensus.sim.Simulation;
import com.example.consensus.sim.SimulationBuilder;
import com.example.consensus.sim.SimulationResult;
import com.example.consensus.sim.StopReason;
import com.example.consensus.sim.trace.MermaidExporter;
import com.example.consensus.sim.trace.MermaidOptions;
import com.example.consensus.sim.trace.TraceEntry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.LongFunction;

/** 테스트들이 공유하는 시나리오 조립과 출력 도구. */
final class PaxosScenarios {

    static final NodeId N1 = new NodeId(1);
    static final NodeId N2 = new NodeId(2);
    static final NodeId N3 = new NodeId(3);
    static final NodeId N4 = new NodeId(4);
    static final NodeId N5 = new NodeId(5);

    static final Path TRACE_DIR = Path.of("build", "paxos-traces");

    private PaxosScenarios() {}

    static Map<NodeId, Object> proposals(Object... idValuePairs) {
        Map<NodeId, Object> map = new TreeMap<>();
        for (int i = 0; i < idValuePairs.length; i += 2) {
            map.put((NodeId) idValuePairs[i], idValuePairs[i + 1]);
        }
        return map;
    }

    static Set<Object> proposedValues(PaxosConfig config) {
        return Set.copyOf(config.proposals().values());
    }

    /** agreement + validity를 건 시뮬레이션 빌더. */
    static SimulationBuilder base(long seed, int nodes, PaxosNode.Builder node, PaxosConfig config, NetworkPolicy network) {
        return Simulation.builder()
                .seed(seed)
                .nodes(nodes, node.factory())
                .network(network)
                .invariant(Invariants.agreement())
                .invariant(PaxosInvariants.validity(proposedValues(config)));
    }

    /** 모든 노드가 결정했는가. */
    static boolean allDecided(com.example.consensus.sim.SimulationView view) {
        return view.decisions().stream().map(Decision::node).distinct().count() >= view.nodes().size();
    }

    /** 결정이 하나라도 나온 실행의 비율과, 그 실행들의 첫 결정 시각 평균. */
    record DecisionStats(int runs, int decidedRuns, int allDecidedRuns, double avgFirstDecisionTime, int violations, int multiRoundRuns) {
        @Override
        public String toString() {
            return String.format("결정 도달 %d/%d (%.1f%%), 전 노드 결정 %d회, 첫 결정 평균 시각 %.1f, 위반 %d건, round 2 이상까지 간 실행 %d회",
                    decidedRuns, runs, 100.0 * decidedRuns / runs, allDecidedRuns, avgFirstDecisionTime, violations, multiRoundRuns);
        }
    }

    static DecisionStats measure(long from, long to, LongFunction<Simulation> factory) {
        int runs = 0, decided = 0, all = 0, violations = 0, multiRound = 0;
        double firstTime = 0;
        for (long seed = from; seed <= to; seed++) {
            SimulationResult r = factory.apply(seed).run();
            runs++;
            if (maxPrepareRound(r) >= 2) {
                multiRound++;
            }
            if (r.reason() == StopReason.INVARIANT_VIOLATED) {
                violations++;
            }
            if (!r.decisions().isEmpty()) {
                decided++;
                firstTime += r.decisions().get(0).time();
            }
            if (r.decisions().stream().map(Decision::node).distinct().count() >= 5) {
                all++;
            }
        }
        return new DecisionStats(runs, decided, all, decided == 0 ? 0 : firstTime / decided, violations, multiRound);
    }

    /** 실행 중 보낸 Prepare의 가장 높은 round. livelock에서 round가 계속 오르는지 확인하는 데 쓴다. */
    static int maxPrepareRound(SimulationResult result) {
        int max = 0;
        for (TraceEntry e : result.trace().entries()) {
            if (e instanceof TraceEntry.Send s && s.message() instanceof PaxosMessage.Prepare p) {
                max = Math.max(max, p.ballot().round());
            }
        }
        return max;
    }

    static void printReport(String title, SeedReport report) {
        System.out.println("[" + title + "]");
        System.out.println(report);
    }

    static Path writeTrace(String fileName, SimulationResult result, int maxLines) {
        try {
            Files.createDirectories(TRACE_DIR);
            Path file = TRACE_DIR.resolve(fileName);
            Files.writeString(file, MermaidExporter.toSequenceDiagram(result.trace(), MermaidOptions.defaults().withMaxLines(maxLines)));
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
