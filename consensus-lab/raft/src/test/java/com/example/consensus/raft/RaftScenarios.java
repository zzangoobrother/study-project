package com.example.consensus.raft;

import com.example.consensus.sim.LossyNetwork;
import com.example.consensus.sim.Invariant;
import com.example.consensus.sim.NetworkPolicy;
import com.example.consensus.sim.NodeId;
import com.example.consensus.sim.PartitionableNetwork;
import com.example.consensus.sim.SeedReport;
import com.example.consensus.sim.SeedRunner;
import com.example.consensus.sim.Simulation;
import com.example.consensus.sim.SimulationBuilder;
import com.example.consensus.sim.SimulationControl;
import com.example.consensus.sim.SimulationView;
import com.example.consensus.sim.trace.MermaidExporter;
import com.example.consensus.sim.trace.MermaidOptions;
import com.example.consensus.sim.SimulationResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.LongFunction;

/** 테스트들이 공유하는 시나리오 조립, 장애 계획, 출력 도구. */
final class RaftScenarios {

    static final NodeId N1 = new NodeId(1);
    static final NodeId N2 = new NodeId(2);
    static final NodeId N3 = new NodeId(3);
    static final NodeId N4 = new NodeId(4);
    static final NodeId N5 = new NodeId(5);
    static final List<NodeId> ALL = List.of(N1, N2, N3, N4, N5);

    static final Path TRACE_DIR = Path.of("build", "raft-traces");

    private RaftScenarios() {}

    /** 정상 구현과 반례 변형을 같은 자리에 끼우기 위한 노드 생성기. */
    @FunctionalInterface
    interface NodeMaker {
        RaftNode create(RaftConfig config, RaftMonitor monitor, RaftWorkload workload);
    }

    /** 시뮬레이션과, 그 안에서 이력을 쌓는 관찰자 한 쌍. */
    record Run(Simulation sim, RaftMonitor monitor) {}

    /** 모든 불변식을 건 시뮬레이션 빌더. 서버 5대. */
    static SimulationBuilder base(long seed, NodeMaker maker, RaftConfig config, RaftWorkload workload,
                                  RaftMonitor monitor, NetworkPolicy network) {
        return base(seed, maker, config, workload, monitor, network, RaftInvariants.all(monitor));
    }

    static SimulationBuilder base(long seed, NodeMaker maker, RaftConfig config, RaftWorkload workload,
                                  RaftMonitor monitor, NetworkPolicy network,
                                  List<Invariant> invariants) {
        SimulationBuilder b = Simulation.builder()
                .seed(seed)
                .nodes(5, id -> maker.create(config, monitor, workload))
                .network(network);
        invariants.forEach(b::invariant);
        return b;
    }

    // ---- 클라이언트: 시각마다 다음 명령 하나를 현재 리더에게 ----

    /** 명령 목록을 순서대로 하나씩 리더에게 넣는 클라이언트. 리더가 없으면 그 시각은 건너뛴다. */
    static final class Ticker {
        private final List<String> commands;
        private int next = 0;

        Ticker(String... commands) {
            this.commands = List.of(commands);
        }

        void tick(SimulationControl control) {
            if (next < commands.size() && RaftClients.submitToLeaders(control, commands.get(next)) > 0) {
                next++;
            }
        }

        boolean done() {
            return next >= commands.size();
        }

        /** from부터 until까지 every 간격으로 tick을 예약한다. */
        void schedule(SimulationBuilder b, long from, long until, long every) {
            for (long t = from; t <= until; t += every) {
                b.at(t, "client", this::tick);
            }
        }
    }

    // ---- 무작위 크래시·재시작·분할 계획 ----

    /**
     * 시드에서 파생한 장애 계획을 시뮬레이션에 예약한다. 계획용 난수는 시뮬레이터 난수와 분리해(시드를 섞어서)
     * 장애 계획이 노드의 난수 소비에 영향을 받지 않게 한다. 같은 시드면 항상 같은 계획이다.
     * 종류: 크래시 40%(동시에 2대까지만), 재시작 30%, 분할 20%(1~2대를 나머지와 가른다), 분할 해소 10%.
     */
    static void randomFaults(SimulationBuilder b, long seed, PartitionableNetwork net, long horizon) {
        Random plan = new Random(seed * 7919L + 13L);
        long t = 100 + plan.nextInt(100);
        while (t < horizon) {
            int kind = plan.nextInt(10);
            int target = plan.nextInt(5);
            int groupMask = 1 + plan.nextInt(30); // 1..30: 5비트 중 일부만 켜진 마스크
            b.at(t, "fault", c -> applyFault(c, net, kind, target, groupMask));
            t += 40 + plan.nextInt(160);
        }
    }

    private static void applyFault(SimulationControl c, PartitionableNetwork net, int kind, int target, int groupMask) {
        SimulationView view = c.view();
        List<NodeId> nodes = view.nodes();
        long down = nodes.stream().filter(n -> !view.isAlive(n)).count();
        if (kind < 4) {
            if (down < 2 && view.isAlive(nodes.get(target))) {
                c.crash(nodes.get(target));
            }
        } else if (kind < 7) {
            for (int i = 0; i < nodes.size(); i++) {
                NodeId n = nodes.get((target + i) % nodes.size());
                if (!view.isAlive(n)) {
                    c.restart(n);
                    break;
                }
            }
        } else if (kind < 9) {
            Set<NodeId> a = new java.util.TreeSet<>();
            Set<NodeId> b = new java.util.TreeSet<>();
            for (int i = 0; i < nodes.size(); i++) {
                ((groupMask >> i & 1) == 1 ? a : b).add(nodes.get(i));
            }
            net.partition(a, b);
        } else {
            net.heal();
        }
    }

    /** 무작위 장애 실험의 공통 조건. 이 값들은 정상 구현과 반례 변형에 똑같이 쓰인다. */
    static final long FAULT_HORIZON = 6_000;
    static final double FAULT_DROP_RATE = 0.1;
    static final int FAULT_WORKLOAD_TOTAL = 400;
    static final int FAULT_WORKLOAD_OUTSTANDING = 3;

    /**
     * 시드 하나의 무작위 장애 실험: 서버 5대, 메시지 10% 유실, 지연 1~10, 가상 시각 6,000까지 크래시·재시작·분할을 뿌린다.
     * 리더가 하트비트 때마다 미커밋 엔트리가 3개 미만이면 명령을 받는다(분할된 옛 리더도 받는다).
     */
    static Run randomFaultRun(long seed, NodeMaker maker) {
        return randomFaultRun(seed, maker, RaftInvariants::all);
    }

    /** 걸 불변식을 고를 수 있는 버전. 어떤 불변식이 먼저 잡는지 가리지 않고 특정 성질만 보고 싶을 때 쓴다. */
    static Run randomFaultRun(long seed, NodeMaker maker, Function<RaftMonitor, List<Invariant>> invariants) {
        RaftMonitor monitor = new RaftMonitor();
        PartitionableNetwork net = new PartitionableNetwork(new LossyNetwork(FAULT_DROP_RATE, 1, 10));
        RaftWorkload workload = new RaftWorkload(FAULT_WORKLOAD_TOTAL, FAULT_WORKLOAD_OUTSTANDING);
        SimulationBuilder b = base(seed, maker, RaftConfig.of(), workload, monitor, net, invariants.apply(monitor))
                .stopWhen(view -> view.now() >= FAULT_HORIZON)
                .maxSteps(2_000_000);
        randomFaults(b, seed, net, FAULT_HORIZON);
        return new Run(b.build(), monitor);
    }

    // ---- 시드 훑기 ----

    /** 시드 범위 실험 결과. violationsByInvariant는 처음 보고된 불변식 이름별 위반 시드 수다. */
    record Sweep(SeedReport report, Map<String, Integer> violationsByInvariant, int seedsWithCommit, double avgCommitted,
                 double avgLeaderTerms) {
        String summary() {
            return String.format(Locale.ROOT, "위반 %d/%d %s, 커밋이 하나라도 있었던 시드 %d, 평균 커밋 index %.1f, 평균 리더 임기 수 %.1f",
                    report.violations(), report.runs(), violationsByInvariant, seedsWithCommit, avgCommitted, avgLeaderTerms);
        }
    }

    static Sweep sweep(long from, long to, LongFunction<Run> factory) {
        SweepTally tally = new SweepTally();
        // SeedRunner는 팩토리가 만든 시뮬레이션을 곧바로 실행하므로, 이전 시드의 관찰자는 다음 팩토리 호출 때 집계한다.
        // (관찰자를 전부 쥐고 있으면 시드 수만큼 이력이 메모리에 남는다.)
        RaftMonitor[] previous = {null};
        SeedReport report = SeedRunner.run(from, to, seed -> {
            tally.add(previous[0]);
            Run run = factory.apply(seed);
            previous[0] = run.monitor();
            return run.sim();
        });
        tally.add(previous[0]);
        return tally.toSweep(report);
    }

    private static final class SweepTally {
        final Map<String, Integer> byInvariant = new TreeMap<>();
        int runs = 0, withCommit = 0;
        double committed = 0, terms = 0;

        void add(RaftMonitor m) {
            if (m == null) {
                return;
            }
            runs++;
            if (m.detectedInvariant() != null) {
                byInvariant.merge(m.detectedInvariant(), 1, Integer::sum);
            }
            withCommit += m.committedCount() > 0 ? 1 : 0;
            committed += m.committedCount();
            terms += m.leaderOfTerm().size();
        }

        Sweep toSweep(SeedReport report) {
            double n = Math.max(runs, 1);
            return new Sweep(report, byInvariant, withCommit, committed / n, terms / n);
        }
    }

    // ---- 출력 보조 ----

    /** "N1[a@1 x@2]" 형태. 크래시한 서버도 디스크 로그를 보여 준다. */
    static String logs(RaftMonitor monitor) {
        StringBuilder sb = new StringBuilder();
        for (NodeId id : ALL) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(id).append(monitor.logOf(id));
        }
        return sb.toString();
    }

    static boolean allApplied(SimulationView view, int count) {
        for (NodeId id : view.nodes()) {
            if (!view.isAlive(id) || view.node(id, RaftNode.class).appliedCommands().size() < count) {
                return false;
            }
        }
        return true;
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
