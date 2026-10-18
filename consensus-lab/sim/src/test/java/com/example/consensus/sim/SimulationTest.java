package com.example.consensus.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.consensus.sim.TestNodes.*;
import com.example.consensus.sim.trace.MermaidExporter;
import com.example.consensus.sim.trace.MermaidOptions;
import com.example.consensus.sim.trace.TraceEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SimulationTest {

    private static final NodeId N1 = new NodeId(1);
    private static final NodeId N2 = new NodeId(2);
    private static final NodeId N3 = new NodeId(3);
    private static final NodeId N4 = new NodeId(4);

    private SimulationResult runBroadcast(long seed, NetworkPolicy network) {
        Map<NodeId, List<Long>> sink = new TreeMap<>();
        return Simulation.builder().seed(seed).nodes(5, id -> new Broadcaster(sink)).network(network).build().run();
    }

    @Test
    @DisplayName("같은 시드로 두 번 실행하면 trace가 완전히 같고, 다른 시드면 달라진다")
    void deterministic() {
        var a = runBroadcast(42L, new LossyNetwork(0.3, 1, 50));
        var b = runBroadcast(42L, new LossyNetwork(0.3, 1, 50));
        var c = runBroadcast(43L, new LossyNetwork(0.3, 1, 50));

        assertThat(a.trace().entries()).isNotEmpty().isEqualTo(b.trace().entries());
        assertThat(a.trace().entries()).isNotEqualTo(c.trace().entries());
    }

    @Test
    @DisplayName("ReliableNetwork에서는 모든 메시지가 배달되고 지연이 범위 안에 있다")
    void reliableDeliversAll() {
        for (long seed = 0; seed < 20; seed++) {
            Map<NodeId, List<Long>> sink = new TreeMap<>();
            var r = Simulation.builder().seed(seed).nodes(4, id -> new Broadcaster(sink))
                    .network(new ReliableNetwork(5, 20)).build().run();

            assertThat(r.reason()).isEqualTo(StopReason.NO_MORE_EVENTS);
            assertThat(r.sent()).isEqualTo(12);
            assertThat(r.delivered()).isEqualTo(12);
            assertThat(r.dropped()).isZero();
            // 모든 Hello는 시각 0에 보냈으므로 도착 시각이 곧 지연이다.
            assertThat(sink.values().stream().flatMap(List::stream)).hasSize(12).allMatch(t -> t >= 5 && t <= 20);
        }
    }

    @Test
    @DisplayName("dropRate가 1.0인 LossyNetwork는 모든 메시지를 유실시킨다")
    void lossyDropsAll() {
        var r = runBroadcast(1L, new LossyNetwork(1.0, 1, 10));

        assertThat(r.sent()).isEqualTo(20);
        assertThat(r.delivered()).isZero();
        assertThat(r.dropped()).isEqualTo(20);
    }

    @Test
    @DisplayName("크래시된 노드의 메시지와 타이머는 버려지고, 재시작하면 Storage만 남고 필드는 초기화된다")
    void crashAndRestart() {
        List<String> log = new ArrayList<>();
        var r = Simulation.builder().seed(7L).nodes(2, id -> new Lifecycle(log))
                .network(new ReliableNetwork(1, 1))
                .crashAt(20, N1).restartAt(30, N1)
                .build().run();

        // N1 크래시(20~30) 중인 25의 poke는 26에 도착하려다 버려지고, 40에 걸었던 첫 세대 타이머는 발화하지 않는다.
        assertThat(log).containsExactly(
                "start counter=0 storageStarts=1",
                "start counter=0 storageStarts=2",
                "timer t2 at=35",
                "poke counter=1 at=61");
        assertThat(r.trace().entries()).anyMatch(e -> e instanceof TraceEntry.Drop d
                && d.to().equals(N1) && d.reason() == DropReason.CRASHED);
        assertThat(r.trace().entries()).anyMatch(e -> e instanceof TraceEntry.TimerDropped t
                && t.node().equals(N1) && "t1".equals(t.key()));
    }

    @Test
    @DisplayName("파티션 중에는 그룹 간 메시지가 유실되고 heal 후에는 배달된다")
    void partitionAndHeal() {
        Map<NodeId, List<Integer>> beats = new TreeMap<>();
        var net = new PartitionableNetwork(new ReliableNetwork(1, 1));
        var r = Simulation.builder().seed(3L).nodes(4, id -> new Beacon(beats)).network(net)
                .at(15, "partition", c -> net.partition(Set.of(N1, N2), Set.of(N3, N4)))
                .at(45, "heal", c -> net.heal())
                .build().run();

        assertThat(beats.get(N2)).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(beats.get(N3)).containsExactly(1, 5, 6);
        assertThat(r.trace().entries()).anyMatch(e -> e instanceof TraceEntry.Drop d && d.reason() == DropReason.PARTITION);
    }

    @Test
    @DisplayName("가장 늦은 이벤트를 먼저 고르는 스케줄러는 실행 순서를 바꾸지만 now는 거꾸로 가지 않는다")
    void customSchedulerKeepsClockMonotonic() {
        Scheduler latestFirst = (pending, view) -> {
            Event best = pending.get(0);
            for (Event e : pending) {
                if (e.at() > best.at() || (e.at() == best.at() && e.seq() > best.seq())) {
                    best = e;
                }
            }
            return best;
        };
        Map<NodeId, List<Long>> normalSink = new TreeMap<>();
        Map<NodeId, List<Long>> lateSink = new TreeMap<>();
        var normal = Simulation.builder().seed(1L).nodes(4, id -> new Broadcaster(normalSink))
                .network(new ReliableNetwork(1, 50)).build().run();
        var late = Simulation.builder().seed(1L).nodes(4, id -> new Broadcaster(lateSink))
                .network(new ReliableNetwork(1, 50)).scheduler(latestFirst).build().run();

        assertThat(late.trace().entries()).isNotEqualTo(normal.trace().entries());
        for (List<Long> times : lateSink.values()) {
            assertThat(times).isSorted();
        }
        assertThat(late.finalTime()).isEqualTo(normal.finalTime());
        // 모든 trace 기록 시각이 단조 증가해야 한다.
        long prev = 0;
        for (TraceEntry e : late.trace().entries()) {
            assertThat(e.time()).isGreaterThanOrEqualTo(prev);
            prev = e.time();
        }
    }

    @Test
    @DisplayName("Invariant 위반 시 INVARIANT_VIOLATED로 멈추고 위반 시점의 step이 기록된다")
    void invariantViolation() {
        var r = Simulation.builder().seed(5L).nodes(3, id -> new Disagreeing(0))
                .invariant(Invariants.agreement()).build().run();

        assertThat(r.reason()).isEqualTo(StopReason.INVARIANT_VIOLATED);
        assertThat(r.violation()).isNotNull();
        assertThat(r.violation().step()).isPositive().isEqualTo(r.steps());
        assertThat(r.violation().message()).contains("agreement");
        assertThat(r.sent()).isGreaterThan(r.delivered()); // 아직 배달되지 않은 메시지를 남기고 멈췄다
    }

    @Test
    @DisplayName("maxSteps에 도달하면 MAX_STEPS로 종료한다")
    void maxSteps() {
        var r = Simulation.builder().seed(1L).nodes(2, id -> new PingPong()).maxSteps(500).build().run();

        assertThat(r.reason()).isEqualTo(StopReason.MAX_STEPS);
        assertThat(r.steps()).isEqualTo(500);
    }

    @Test
    @DisplayName("stopWhen 조건이 만족되면 STOP_CONDITION으로 종료한다")
    void stopCondition() {
        var r = Simulation.builder().seed(1L).nodes(2, id -> new PingPong())
                .stopWhen(v -> v.steps() >= 10).build().run();

        assertThat(r.reason()).isEqualTo(StopReason.STOP_CONDITION);
        assertThat(r.steps()).isEqualTo(10);
    }

    @Test
    @DisplayName("send의 from은 항상 실제 발신 노드이며 내용으로 위조할 수 없다")
    void fromCannotBeForged() {
        Map<NodeId, List<NodeId>> observed = new TreeMap<>();
        var r = Simulation.builder().seed(1L).nodes(4, id -> new Liar(observed)).build().run();

        assertThat(observed.values().stream().flatMap(List::stream)).hasSize(3).containsOnly(N3);
        assertThat(r.trace().entries().stream().filter(e -> e instanceof TraceEntry.Deliver))
                .allMatch(e -> ((TraceEntry.Deliver) e).from().equals(N3));
    }

    @Test
    @DisplayName("SeedRunner가 위반이 난 시드를 정확히 수집하고 최대 10개까지만 보여 준다")
    void seedRunnerCollectsViolations() {
        // 시드가 3의 배수일 때만 노드별로 다른 값을 결정해 agreement를 깬다.
        java.util.function.LongFunction<Simulation> factory = seed -> Simulation.builder().seed(seed)
                .nodes(3, id -> new Disagreeing(seed % 3 == 0 ? 0 : 99))
                .invariant(Invariants.agreement()).build();

        SeedReport small = SeedRunner.run(0, 14, factory);
        assertThat(small.runs()).isEqualTo(15);
        assertThat(small.violations()).isEqualTo(5);
        assertThat(small.firstViolationSeeds()).containsExactly(0L, 3L, 6L, 9L, 12L);
        assertThat(small.reasonCounts().get(StopReason.INVARIANT_VIOLATED)).isEqualTo(5);
        assertThat(small.reasonCounts().get(StopReason.NO_MORE_EVENTS)).isEqualTo(10);

        SeedReport large = SeedRunner.run(0, 39, factory);
        assertThat(large.violations()).isEqualTo(14);
        assertThat(large.firstViolationSeeds()).containsExactly(0L, 3L, 6L, 9L, 12L, 15L, 18L, 21L, 24L, 27L);
        assertThat(large.toString()).contains("위반: 14건").contains("INVARIANT_VIOLATED=14");
    }

    @Test
    @DisplayName("MermaidExporter가 deliver, drop, Note를 기대한 형식으로 출력한다")
    void mermaid() {
        List<String> log = new ArrayList<>();
        var r = Simulation.builder().seed(7L).nodes(2, id -> new Lifecycle(log))
                .network(new ReliableNetwork(1, 1))
                .crashAt(20, N1).restartAt(30, N1)
                .at(70, "decide-now", c -> {})
                .build().run();

        String diagram = MermaidExporter.toSequenceDiagram(r.trace(), MermaidOptions.defaults());

        assertThat(diagram).startsWith("sequenceDiagram\n")
                .contains("    participant N1\n")
                .contains("    N2->>N1: Poke[]\n")
                .contains("    N2-xN1: Poke[] (dropped)\n")
                .contains("    Note over N1: crash\n")
                .contains("    Note over N1: restart\n")
                .contains("    %% action: decide-now\n");

        String limited = MermaidExporter.toSequenceDiagram(r.trace(), MermaidOptions.defaults().withMaxLines(2));
        assertThat(limited).contains("줄 생략");
        assertThat(limited.lines().filter(l -> l.startsWith("    ") && !l.contains("participant") && !l.contains("%%")))
                .hasSize(2);

        String onlyN2 = MermaidExporter.toSequenceDiagram(r.trace(), MermaidOptions.defaults().onlyNodes(N2));
        assertThat(onlyN2).doesNotContain("N1");
    }

    @Test
    @DisplayName("결정 기록은 (node, time, value)로 남고 Mermaid에 decide Note로 나온다")
    void decisionRecorded() {
        var r = Simulation.builder().seed(5L).nodes(3, id -> new Disagreeing(7)).build().run();

        assertThat(r.decisions()).hasSize(3).allMatch(d -> d.value().equals(7));
        assertThat(MermaidExporter.toSequenceDiagram(r.trace(), MermaidOptions.defaults()))
                .contains("Note over N1: decide 7");
    }
}
