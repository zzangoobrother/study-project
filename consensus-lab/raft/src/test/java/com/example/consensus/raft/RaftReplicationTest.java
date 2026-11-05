package com.example.consensus.raft;

import static com.example.consensus.raft.RaftScenarios.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.consensus.sim.LossyNetwork;
import com.example.consensus.sim.NodeId;
import com.example.consensus.sim.PartitionableNetwork;
import com.example.consensus.sim.ReliableNetwork;
import com.example.consensus.sim.SimulationResult;
import com.example.consensus.sim.StopReason;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 리더 선출, 로그 복제·커밋·적용의 정상 동작, 리더 크래시 복구, 네트워크 분할. 모든 불변식을 매 이벤트 검사한다. */
class RaftReplicationTest {

    private static final RaftConfig CONFIG = RaftConfig.of();
    private static final RaftScenarios.NodeMaker CORRECT = RaftNode::new;

    @Test
    @DisplayName("정상: 5대에 명령 20개를 넣으면 모든 서버가 같은 순서로 적용한다")
    void normalOperation() {
        List<String> expected = IntStream.rangeClosed(1, 20).mapToObj(i -> "cmd-" + i).toList();
        int seeds = 1_000;
        int identical = 0, violations = 0;
        double leaderTime = 0, elections = 0, finishTime = 0, sent = 0;
        long minLeaderTime = Long.MAX_VALUE, maxLeaderTime = 0;
        for (long seed = 1; seed <= seeds; seed++) {
            RaftMonitor monitor = new RaftMonitor();
            Ticker client = new Ticker(expected.toArray(new String[0]));
            boolean[] same = {false};
            var b = base(seed, CORRECT, CONFIG, RaftWorkload.none(), monitor, new ReliableNetwork(1, 10))
                    .stopWhen(view -> {
                        if (!allApplied(view, expected.size())) {
                            return false;
                        }
                        same[0] = ALL.stream().allMatch(id -> view.node(id, RaftNode.class).appliedCommands().equals(expected));
                        return true;
                    })
                    .maxSteps(200_000);
            client.schedule(b, 0, 3_000, 20);
            SimulationResult r = b.build().run();

            violations += r.reason() == StopReason.INVARIANT_VIOLATED ? 1 : 0;
            identical += same[0] ? 1 : 0;
            long lt = monitor.firstLeaderTime();
            leaderTime += lt;
            minLeaderTime = Math.min(minLeaderTime, lt);
            maxLeaderTime = Math.max(maxLeaderTime, lt);
            elections += monitor.electionsStarted();
            finishTime += r.finalTime();
            sent += r.sent();
        }
        System.out.println(String.format(Locale.ROOT,
                "[RAFT] 정상(5대, 명령 20개, ReliableNetwork(1,10), 선거 타임아웃 [150,300], 하트비트 50, 시드 1..%d): "
                        + "전원 동일 순서 적용 %d/%d, 위반 %d, 첫 리더 선출 평균 %.1f (최소 %d, 최대 %d), 선거 시작 평균 %.2f회, "
                        + "전원 적용 완료 평균 시각 %.1f, 보낸 메시지 평균 %.0f",
                seeds, identical, seeds, violations, leaderTime / seeds, minLeaderTime, maxLeaderTime, elections / seeds,
                finishTime / seeds, sent / seeds));
        assertThat(violations).isZero();
        assertThat(identical).isEqualTo(seeds);
    }

    /** 리더 크래시 시나리오 한 번의 측정값. */
    private static final class CrashProbe {
        NodeId lagging;
        NodeId oldLeader;
        long oldTerm;
        boolean sameLists;
    }

    @Test
    @DisplayName("리더 크래시: 새 리더가 뽑혀 이후 명령도 커밋하고, 죽었던 서버들은 재시작 후 따라잡는다")
    void leaderCrashAndCatchUp() {
        List<String> expected = new ArrayList<>();
        List.of("a1", "a2", "a3").forEach(expected::add);
        IntStream.rangeClosed(1, 10).mapToObj(i -> "m" + i).forEach(expected::add);
        IntStream.rangeClosed(1, 5).mapToObj(i -> "b" + i).forEach(expected::add);
        int total = expected.size(); // 18

        int seeds = 200;
        int success = 0, violations = 0;
        double newLeaderDelay = 0, laggingCatchUp13 = 0, laggingCatchUpAll = 0, laggingDec = 0, oldCatchUp = 0, oldDec = 0;
        long maxDec = 0;
        for (long seed = 1; seed <= seeds; seed++) {
            RaftMonitor monitor = new RaftMonitor();
            CrashProbe probe = new CrashProbe();
            var b = base(seed, CORRECT, CONFIG, RaftWorkload.none(), monitor, new ReliableNetwork(1, 10))
                    .stopWhen(view -> {
                        if (view.now() >= 3_500 && allApplied(view, total)) {
                            probe.sameLists = ALL.stream().allMatch(id -> view.node(id, RaftNode.class).appliedCommands().equals(expected));
                            return true;
                        }
                        return view.now() > 8_000;
                    })
                    .maxSteps(500_000);
            new Ticker("a1", "a2", "a3").schedule(b, 600, 1_400, 20);
            // 1,500: 팔로워 하나를 죽인다. 이 서버는 이후 커밋되는 m1..m10을 놓친다.
            b.at(1_500, "crash-follower", c -> {
                for (NodeId id : ALL) {
                    if (c.view().isAlive(id) && c.view().node(id, RaftNode.class).role() != Role.LEADER) {
                        probe.lagging = id;
                        c.crash(id);
                        return;
                    }
                }
            });
            new Ticker("m1", "m2", "m3", "m4", "m5", "m6", "m7", "m8", "m9", "m10").schedule(b, 1_520, 2_100, 20);
            // 2,200: 리더를 죽인다.
            b.at(2_200, "crash-leader", c -> {
                probe.oldLeader = RaftClients.currentLeader(c.view());
                probe.oldTerm = c.view().node(probe.oldLeader, RaftNode.class).currentTerm();
                c.crash(probe.oldLeader);
            });
            // 2,215: 뒤처졌던 서버를 되살린다. 옛 리더가 죽기 직전에 보낸 메시지(지연 최대 10)가 모두 도착한 뒤여야 한다.
            // 그렇지 않으면 그 메시지가 재시작한 서버에 닿아 밀린 엔트리를 한꺼번에 채워 버린다.
            // 새 리더는 이 서버가 몇 개나 뒤처졌는지 모른 채 시작하므로 nextIndex를 한 칸씩 물려 가며 일치 지점을 찾는다.
            b.at(2_215, "restart-lagging", c -> c.restart(probe.lagging));
            new Ticker("b1", "b2", "b3", "b4", "b5").schedule(b, 2_220, 4_000, 20);
            b.at(3_500, "restart-old-leader", c -> c.restart(probe.oldLeader));
            SimulationResult r = b.build().run();

            violations += r.reason() == StopReason.INVARIANT_VIOLATED ? 1 : 0;
            if (r.reason() == StopReason.STOP_CONDITION && probe.sameLists) {
                success++;
            } else {
                continue;
            }
            long newLeaderAt = monitor.leaderEvents().stream()
                    .filter(e -> e.time() > 2_200 && e.term() > probe.oldTerm).findFirst().orElseThrow().time();
            newLeaderDelay += newLeaderAt - 2_200;
            laggingCatchUp13 += monitor.appliedTime(probe.lagging, 13) - 2_215;
            laggingCatchUpAll += monitor.appliedTime(probe.lagging, total) - 2_215;
            laggingDec += monitor.decrementsFor(probe.lagging);
            maxDec = Math.max(maxDec, monitor.decrementsFor(probe.lagging));
            oldCatchUp += monitor.appliedTime(probe.oldLeader, total) - 3_500;
            oldDec += monitor.decrementsFor(probe.oldLeader);
        }
        double n = Math.max(success, 1);
        System.out.println(String.format(Locale.ROOT,
                "[RAFT] 리더 크래시(시드 1..%d, 평균): 최종 5대 동일 목록 %d/%d, 위반 %d, 리더 크래시 후 새 리더 선출까지 +%.1f, "
                        + "뒤처졌다 재시작한 서버(10개 뒤처짐): 재시작(2,215) 후 index 13까지 적용 +%.1f / 18개 전부 +%.1f, nextIndex 감소 평균 %.1f회(최대 %d), "
                        + "옛 리더(2,200 크래시, 3,500 재시작): 재시작 후 전부 적용 +%.1f, nextIndex 감소 평균 %.2f회",
                seeds, success, seeds, violations, newLeaderDelay / n, laggingCatchUp13 / n, laggingCatchUpAll / n,
                laggingDec / n, maxDec, oldCatchUp / n, oldDec / n));
        assertThat(violations).isZero();
        assertThat(success).isEqualTo(seeds);
    }

    /** 분할 시나리오 한 번의 측정값. */
    private static final class PartitionProbe {
        NodeId oldLeader;
        NodeId follower;
        NodeId newLeader;
        long oldTerm;
        long newTerm;
        // 분할 중(2,500)
        Role oldRoleDuring;
        int oldCommitDuring;
        List<LogEntry> oldLogDuring;
        List<String> newLeaderApplied;
        // 분할 해소 후(3,200)
        Role oldRoleAfter;
        List<List<LogEntry>> logsAfter = new ArrayList<>();
        List<List<String>> appliedAfter = new ArrayList<>();
    }

    @Test
    @DisplayName("네트워크 분할: 소수 쪽 옛 리더의 명령은 커밋되지 않고, 분할이 풀리면 덮어써진다")
    void partitionedLeader() {
        int seeds = 200;
        int ok = 0, violations = 0;
        double newLeaderDelay = 0;
        for (long seed = 1; seed <= seeds; seed++) {
            RaftMonitor monitor = new RaftMonitor();
            PartitionProbe p = new PartitionProbe();
            PartitionableNetwork net = new PartitionableNetwork(new ReliableNetwork(1, 10));
            var b = base(seed, CORRECT, CONFIG, RaftWorkload.none(), monitor, net)
                    .stopWhen(view -> view.now() >= 3_300)
                    .maxSteps(500_000);
            new Ticker("pre-1", "pre-2", "pre-3").schedule(b, 500, 960, 20);
            // 1,000: 리더와 팔로워 하나(소수 2대)를 나머지 3대와 가른다.
            b.at(1_000, "partition", c -> {
                p.oldLeader = RaftClients.currentLeader(c.view());
                p.oldTerm = c.view().node(p.oldLeader, RaftNode.class).currentTerm();
                p.follower = ALL.stream().filter(id -> !id.equals(p.oldLeader)).findFirst().orElseThrow();
                var minority = java.util.Set.of(p.oldLeader, p.follower);
                var majority = new java.util.TreeSet<>(ALL);
                majority.removeAll(minority);
                net.partition(minority, majority);
            });
            // 옛 리더는 자기가 아직 리더라고 믿으므로 이 명령을 받아 준다. 하지만 과반에 닿을 수 없다.
            b.at(1_010, "lost-1", c -> RaftClients.submitTo(c, p.oldLeader, "lost-1"));
            b.at(1_020, "lost-2", c -> RaftClients.submitTo(c, p.oldLeader, "lost-2"));
            // 2,000: 다수 쪽에 새 리더가 있어야 한다. 새 리더에게 명령을 넣는다.
            b.at(2_000, "win-1", c -> {
                p.newLeader = RaftClients.currentLeader(c.view());
                p.newTerm = c.view().node(p.newLeader, RaftNode.class).currentTerm();
                RaftClients.submitTo(c, p.newLeader, "win-1");
            });
            b.at(2_020, "win-2", c -> RaftClients.submitTo(c, p.newLeader, "win-2"));
            b.at(2_500, "during", c -> {
                RaftNode old = c.view().node(p.oldLeader, RaftNode.class);
                p.oldRoleDuring = old.role();
                p.oldCommitDuring = old.commitIndex();
                p.oldLogDuring = old.log();
                p.newLeaderApplied = c.view().node(p.newLeader, RaftNode.class).appliedCommands();
            });
            b.at(3_000, "heal", c -> net.heal());
            b.at(3_200, "after", c -> {
                p.oldRoleAfter = c.view().node(p.oldLeader, RaftNode.class).role();
                for (NodeId id : ALL) {
                    p.logsAfter.add(c.view().node(id, RaftNode.class).log());
                    p.appliedAfter.add(c.view().node(id, RaftNode.class).appliedCommands());
                }
            });
            SimulationResult r = b.build().run();

            violations += r.reason() == StopReason.INVARIANT_VIOLATED ? 1 : 0;
            List<String> finalApplied = List.of("pre-1", "pre-2", "pre-3", "win-1", "win-2");
            boolean good = p.oldLeader != null && !p.newLeader.equals(p.oldLeader) && p.newTerm > p.oldTerm
                    && p.oldRoleDuring == Role.LEADER                      // 갇힌 옛 리더는 여전히 자기가 리더인 줄 안다
                    && p.oldCommitDuring == 3                              // pre-1..3까지만 커밋, lost-1..2는 커밋 안 됨
                    && p.oldLogDuring.size() == 5
                    && p.oldLogDuring.get(3).command().equals("lost-1") && p.oldLogDuring.get(4).command().equals("lost-2")
                    && p.newLeaderApplied.equals(finalApplied)             // 다수 쪽은 win-1..2를 커밋했다
                    && p.oldRoleAfter == Role.FOLLOWER                     // 분할이 풀리자 옛 리더가 내려갔다
                    && p.appliedAfter.stream().allMatch(finalApplied::equals)
                    && p.logsAfter.stream().allMatch(l -> l.equals(p.logsAfter.get(0)))
                    && p.logsAfter.get(0).stream().noneMatch(e -> e.command().startsWith("lost"));
            ok += good ? 1 : 0;
            newLeaderDelay += monitor.leaderEvents().stream()
                    .filter(e -> e.time() > 1_000 && e.term() > p.oldTerm).findFirst().orElseThrow().time() - 1_000;
        }
        System.out.println(String.format(Locale.ROOT,
                "[RAFT] 분할(시드 1..%d): 리더+팔로워 2대가 소수 쪽에 갇힘. 소수 쪽 lost-1,2 미커밋 + 다수 쪽 새 리더(임기 증가)가 win-1,2 커밋 + "
                        + "해소 후 옛 리더 강등·lost 엔트리 덮어씀·5대 로그/적용 목록 동일 = %d/%d, 불변식 위반 %d, 분할 후 새 리더 선출까지 평균 +%.1f",
                seeds, ok, seeds, violations, newLeaderDelay / seeds));
        assertThat(violations).isZero();
        assertThat(ok).isEqualTo(seeds);
    }
}
