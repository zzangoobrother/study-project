package com.example.consensus.raft;

import static com.example.consensus.raft.RaftScenarios.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.consensus.raft.broken.DirectCommitNode;
import com.example.consensus.raft.broken.NoVoteRestrictionNode;
import com.example.consensus.sim.Invariant;
import com.example.consensus.sim.NodeId;
import com.example.consensus.sim.PartitionableNetwork;
import com.example.consensus.sim.ReliableNetwork;
import com.example.consensus.sim.SimulationResult;
import com.example.consensus.sim.SimulationView;
import com.example.consensus.sim.StopReason;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 규칙 하나를 빼면 안전성이 깨진다는 것을 두 방식으로 보인다.
 * (a) 크래시·분할 스크립트로 위반 순간을 직접 구성한다(결정적 시나리오).
 * (b) 시드 1,000개의 무작위 장애로 위반 시드를 센다.
 * 정상 구현은 같은 스크립트·같은 조건에서 위반이 없다.
 */
class RaftCounterexampleTest {

    /** 스크립트용 설정: 선거 타임아웃을 사실상 끄고(선거는 campaign()으로만), 하트비트 20, 지연 2 고정. */
    private static final RaftConfig SCRIPT = RaftConfig.of().withElectionTimeout(100_000, 100_000).withHeartbeatInterval(20);

    private static final List<String> INVARIANT_NAMES = List.of(
            RaftInvariants.ELECTION_SAFETY, RaftInvariants.LOG_MATCHING,
            RaftInvariants.STATE_MACHINE_SAFETY, RaftInvariants.LEADER_COMPLETENESS);

    /** 스크립트 실행 결과: 시뮬레이션 결과, 관찰자, 단계별 로그 스냅샷, 마지막에 각 서버가 적용한 명령 목록. */
    record Script(SimulationResult result, RaftMonitor monitor, List<String> snapshots, List<List<String>> finalApplied) {
        boolean violated() {
            return result.reason() == StopReason.INVARIANT_VIOLATED;
        }

        String violation() {
            return violated() ? "t=" + result.violation().time() + " " + result.violation().message() : "위반 없음";
        }
    }

    private static List<Invariant> only(RaftMonitor m, String name) {
        return switch (name) {
            case RaftInvariants.ELECTION_SAFETY -> List.of(RaftInvariants.electionSafety(m));
            case RaftInvariants.LOG_MATCHING -> List.of(RaftInvariants.logMatching(m));
            case RaftInvariants.STATE_MACHINE_SAFETY -> List.of(RaftInvariants.stateMachineSafety(m));
            default -> List.of(RaftInvariants.leaderCompleteness(m));
        };
    }

    /** 서버별 "N1(리더 term4)[a@1, x@2]" 한 줄. 크래시한 서버는 디스크 로그를 보여 준다. */
    private static String describe(SimulationView view, RaftMonitor monitor) {
        StringBuilder sb = new StringBuilder();
        for (NodeId id : ALL) {
            String state = "죽음";
            if (view.isAlive(id)) {
                RaftNode n = view.node(id, RaftNode.class);
                state = switch (n.role()) {
                    case LEADER -> "리더";
                    case CANDIDATE -> "후보";
                    case FOLLOWER -> "팔로워";
                } + " term" + n.currentTerm() + " commit" + n.commitIndex();
            }
            sb.append(id).append('(').append(state).append(')').append(monitor.logOf(id)).append("  ");
        }
        return sb.toString().trim();
    }

    // =====================================================================================
    // 그림 8: 이전 임기 엔트리를 과반 복제만으로 커밋하면 커밋된 엔트리가 덮어써진다
    // =====================================================================================

    /**
     * 논문 그림 8을 순서대로 재현한다. 선거는 campaign()으로 직접 일으키고 분할·크래시로 복제 범위를 정한다.
     * <pre>
     * (a) S1 리더(term 2). x를 index 2에 만들어 S2에만 복제한 채 (과반 아님)
     * (b) S1 크래시. S5가 S3,S4의 표로 term 3 리더가 되어 y를 index 2에 쓰지만 아무에게도 복제하지 못한 채
     * (c) S5 크래시. S1 재시작, term 4 리더. x를 S3에 복제 -> {S1,S2,S3}가 term 2 엔트리 x를 가짐
     *     정상: log[2].term(2) != currentTerm(4)이므로 커밋하지 않는다. 변형: 과반이니 커밋하고 적용한다
     * (d) S1 크래시. S5 재시작, term 5 리더(S2,S3,S4 표: S5의 마지막 term 3이 더 최신). y를 모두에게 덮어쓴다
     * </pre>
     */
    private static Script figure8(NodeMaker maker, Function<RaftMonitor, List<Invariant>> invariants) {
        RaftMonitor monitor = new RaftMonitor();
        PartitionableNetwork net = new PartitionableNetwork(new ReliableNetwork(2, 2));
        List<String> snapshots = new ArrayList<>();
        List<List<String>> finalApplied = new ArrayList<>();
        var b = base(1, maker, SCRIPT, RaftWorkload.none(), monitor, net, invariants.apply(monitor))
                .stopWhen(view -> view.now() >= 450)
                .maxSteps(100_000);

        // 준비: S1이 term 1 리더가 되어 a를 index 1에 모두에게 커밋한다.
        b.at(10, "N1 campaign (term 1)", c -> c.view().node(N1, RaftNode.class).campaign());
        b.at(50, "N1 <- a", c -> RaftClients.submitTo(c, N1, "a"));
        // (a) S1이 재시작해 term 2 리더가 되고, x를 S2에만 복제한다.
        b.at(100, "crash N1", c -> c.crash(N1));
        b.at(101, "restart N1", c -> c.restart(N1));
        b.at(102, "N1 campaign (term 2)", c -> c.view().node(N1, RaftNode.class).campaign());
        b.at(150, "partition {N1,N2} | N3 | N4 | N5", c -> net.partition(Set.of(N1, N2), Set.of(N3), Set.of(N4), Set.of(N5)));
        b.at(151, "N1 <- x", c -> RaftClients.submitTo(c, N1, "x"));
        b.at(190, "snapshot (a)", c -> snapshots.add("(a) t=190 " + describe(c.view(), monitor)));
        // (b) S1 크래시, S5가 term 3 리더가 되어 y를 혼자 쓴다.
        b.at(200, "crash N1", c -> c.crash(N1));
        b.at(201, "heal", c -> net.heal());
        b.at(202, "N5 campaign (term 3)", c -> c.view().node(N5, RaftNode.class).campaign());
        b.at(210, "isolate N5", c -> net.partition(Set.of(N5), Set.of(N1, N2, N3, N4)));
        b.at(211, "N5 <- y", c -> RaftClients.submitTo(c, N5, "y"));
        b.at(240, "snapshot (b)", c -> snapshots.add("(b) t=240 " + describe(c.view(), monitor)));
        // (c) S5 크래시, S1 재시작. 첫 시도(term 3)는 표가 모자라 실패하고 두 번째(term 4)에 리더가 된다. x가 S3까지 복제된다.
        b.at(250, "crash N5", c -> c.crash(N5));
        b.at(251, "partition {N1,N2,N3} | N4 | N5", c -> net.partition(Set.of(N1, N2, N3), Set.of(N4), Set.of(N5)));
        b.at(252, "restart N1", c -> c.restart(N1));
        b.at(253, "N1 campaign (term 3, 실패)", c -> c.view().node(N1, RaftNode.class).campaign());
        b.at(270, "N1 campaign (term 4)", c -> c.view().node(N1, RaftNode.class).campaign());
        b.at(320, "snapshot (c)", c -> snapshots.add("(c) t=320 " + describe(c.view(), monitor)));
        // (d) S1 크래시, S5 재시작. 첫 시도(term 4)는 실패, 두 번째(term 5)에 S2,S3,S4의 표로 리더가 된다.
        b.at(330, "crash N1", c -> c.crash(N1));
        b.at(331, "heal", c -> net.heal());
        b.at(332, "restart N5", c -> c.restart(N5));
        b.at(333, "N5 campaign (term 4, 실패)", c -> c.view().node(N5, RaftNode.class).campaign());
        b.at(350, "N5 campaign (term 5)", c -> c.view().node(N5, RaftNode.class).campaign());
        b.at(360, "N5 <- z", c -> RaftClients.submitTo(c, N5, "z"));
        b.at(400, "snapshot (d)", c -> snapshots.add("(d) t=400 " + describe(c.view(), monitor)));
        b.at(410, "restart N1", c -> c.restart(N1));
        b.at(440, "final", c -> {
            snapshots.add("(끝) t=440 " + describe(c.view(), monitor));
            for (NodeId id : ALL) {
                finalApplied.add(c.view().node(id, RaftNode.class).appliedCommands());
            }
        });
        return new Script(b.build().run(), monitor, snapshots, finalApplied);
    }

    @Test
    @DisplayName("그림 8: 정상 구현은 term 2 엔트리 x를 커밋하지 않아 덮어써도 안전하다")
    void figure8Correct() {
        Script s = figure8(RaftNode::new, RaftInvariants::all);
        s.snapshots().forEach(line -> System.out.println("[RAFT] 그림8 정상 " + line));
        System.out.println("[RAFT] 그림8 정상: " + s.violation() + ", 서버별 최종 적용 목록 " + s.finalApplied());
        assertThat(s.violated()).isFalse();
        // x는 어느 서버도 커밋·적용하지 않았고, 마지막에 5대가 모두 [a, y, z]를 적용한다.
        assertThat(s.finalApplied()).hasSize(5).allSatisfy(applied -> assertThat(applied).containsExactly("a", "y", "z"));
    }

    @Test
    @DisplayName("그림 8: 이전 임기 엔트리를 직접 커밋하면 커밋·적용된 x가 y로 덮어써진다")
    void figure8DirectCommit() {
        Script all = figure8(DirectCommitNode::new, RaftInvariants::all);
        all.snapshots().forEach(line -> System.out.println("[RAFT] 그림8 변형 " + line));
        System.out.println("[RAFT] 그림8 변형(DirectCommitNode, 불변식 전부): " + all.violation());
        assertThat(all.violated()).isTrue();

        // 불변식을 하나씩만 걸어 각각이 언제 잡는지 본다.
        StringBuilder perInvariant = new StringBuilder();
        for (String name : INVARIANT_NAMES) {
            Script one = figure8(DirectCommitNode::new, m -> only(m, name));
            perInvariant.append(name).append(": ").append(one.violated() ? "t=" + one.result().violation().time() : "감지 못함").append(" / ");
        }
        System.out.println("[RAFT] 그림8 변형 불변식별 최초 감지 시각: " + perInvariant);

        // 아무 불변식도 걸지 않고 끝까지 돌려, 커밋된 x가 실제로 사라지는 결과를 본다.
        Script none = figure8(DirectCommitNode::new, m -> List.of());
        System.out.println("[RAFT] 그림8 변형(불변식 없음) 서버별 최종 적용 목록 " + none.finalApplied());
        none.snapshots().forEach(line -> System.out.println("[RAFT] 그림8 변형(불변식 없음) " + line));
        assertThat(none.finalApplied().stream().flatMap(List::stream)).contains("x", "y");

        writeTrace("figure8-direct-commit.mmd", all.result(), 400);
        assertThat(all.result().violation().message()).isNotBlank();
    }

    // =====================================================================================
    // 투표 제한: 없으면 뒤처진 서버가 당선되어 커밋된 엔트리를 지운다
    // =====================================================================================

    /**
     * <pre>
     * S1이 term 1 리더. S4,S5는 분할되어 로그가 비어 있다. a를 {S1,S2,S3}에 커밋한다.
     * S1 크래시, 분할 해소. 로그가 빈 S4가 후보가 된다(term 2).
     *   정상: S2,S3는 S4의 로그가 자기보다 덜 최신이므로 거절 -> S4 낙선. 이후 S2가 당선되어 a가 살아남는다.
     *   변형: S2,S3,S5가 모두 투표 -> S4가 리더. b를 쓰면 S2,S3의 커밋된 a가 b로 덮어써진다.
     * </pre>
     */
    private static Script voteRestriction(NodeMaker maker, Function<RaftMonitor, List<Invariant>> invariants) {
        RaftMonitor monitor = new RaftMonitor();
        PartitionableNetwork net = new PartitionableNetwork(new ReliableNetwork(2, 2));
        List<String> snapshots = new ArrayList<>();
        List<List<String>> finalApplied = new ArrayList<>();
        var b = base(1, maker, SCRIPT, RaftWorkload.none(), monitor, net, invariants.apply(monitor))
                .stopWhen(view -> view.now() >= 260)
                .maxSteps(100_000);
        b.at(10, "N1 campaign (term 1)", c -> c.view().node(N1, RaftNode.class).campaign());
        b.at(20, "partition {N1,N2,N3} | N4 | N5", c -> net.partition(Set.of(N1, N2, N3), Set.of(N4), Set.of(N5)));
        b.at(50, "N1 <- a", c -> RaftClients.submitTo(c, N1, "a"));
        b.at(100, "snapshot (1)", c -> snapshots.add("(1) t=100 " + describe(c.view(), monitor)));
        b.at(110, "crash N1", c -> c.crash(N1));
        b.at(111, "heal", c -> net.heal());
        b.at(112, "N4 campaign (term 2)", c -> c.view().node(N4, RaftNode.class).campaign());
        b.at(125, "snapshot (2)", c -> snapshots.add("(2) t=125 " + describe(c.view(), monitor)));
        b.at(130, "N4 <- b", c -> RaftClients.submitTo(c, N4, "b"));
        b.at(145, "snapshot (3)", c -> snapshots.add("(3) t=145 " + describe(c.view(), monitor)));
        b.at(150, "N2 campaign (term 3)", c -> c.view().node(N2, RaftNode.class).campaign());
        b.at(170, "N2 <- c", c -> RaftClients.submitTo(c, N2, "c"));
        b.at(250, "final", c -> {
            snapshots.add("(끝) t=250 " + describe(c.view(), monitor));
            for (NodeId id : ALL) {
                if (c.view().isAlive(id)) {
                    finalApplied.add(c.view().node(id, RaftNode.class).appliedCommands());
                }
            }
        });
        return new Script(b.build().run(), monitor, snapshots, finalApplied);
    }

    @Test
    @DisplayName("투표 제한: 정상 구현은 뒤처진 후보에게 표를 주지 않아 커밋된 a가 살아남는다")
    void voteRestrictionCorrect() {
        Script s = voteRestriction(RaftNode::new, RaftInvariants::all);
        s.snapshots().forEach(line -> System.out.println("[RAFT] 투표제한 정상 " + line));
        System.out.println("[RAFT] 투표제한 정상: " + s.violation() + ", 살아 있는 서버의 최종 적용 목록 " + s.finalApplied());
        assertThat(s.violated()).isFalse();
        assertThat(s.finalApplied()).hasSize(4).allSatisfy(applied -> assertThat(applied).containsExactly("a", "c"));
    }

    @Test
    @DisplayName("투표 제한 제거: 로그가 빈 후보가 당선되어 커밋된 a를 b로 덮어쓴다")
    void voteRestrictionRemoved() {
        Script all = voteRestriction(NoVoteRestrictionNode::new, RaftInvariants::all);
        all.snapshots().forEach(line -> System.out.println("[RAFT] 투표제한 변형 " + line));
        System.out.println("[RAFT] 투표제한 변형(NoVoteRestrictionNode, 불변식 전부): " + all.violation());
        assertThat(all.violated()).isTrue();

        StringBuilder perInvariant = new StringBuilder();
        for (String name : INVARIANT_NAMES) {
            Script one = voteRestriction(NoVoteRestrictionNode::new, m -> only(m, name));
            perInvariant.append(name).append(": ").append(one.violated() ? "t=" + one.result().violation().time() : "감지 못함").append(" / ");
        }
        System.out.println("[RAFT] 투표제한 변형 불변식별 최초 감지 시각: " + perInvariant);

        Script none = voteRestriction(NoVoteRestrictionNode::new, m -> List.of());
        System.out.println("[RAFT] 투표제한 변형(불변식 없음) 살아 있는 서버의 최종 적용 목록 " + none.finalApplied());
        none.snapshots().forEach(line -> System.out.println("[RAFT] 투표제한 변형(불변식 없음) " + line));
        assertThat(none.finalApplied().stream().flatMap(List::stream)).contains("a", "b");
        writeTrace("vote-restriction-removed.mmd", all.result(), 400);
    }

    // =====================================================================================
    // 무작위 장애 시드 1,000개
    // =====================================================================================

    private static final int FROM = 1;
    private static final int TO = 1_000;

    @Test
    @DisplayName("무작위 장애 1,000 시드: 투표 제한 제거 변형의 위반 시드 수")
    void randomNoVoteRestriction() {
        Sweep correct = sweep(FROM, TO, seed -> randomFaultRun(seed, RaftNode::new));
        Sweep broken = sweep(FROM, TO, seed -> randomFaultRun(seed, NoVoteRestrictionNode::new));
        // 같은 시드를 State Machine Safety만 걸고 다시 돌려, 커밋된 엔트리 소실이 실제 상태 머신 분기로 이어진 시드를 센다.
        Sweep brokenSmsOnly = sweep(FROM, TO, seed -> randomFaultRun(seed, NoVoteRestrictionNode::new, m -> only(m, RaftInvariants.STATE_MACHINE_SAFETY)));
        System.out.println("[RAFT] 무작위 장애 시드 " + FROM + ".." + TO + " 정상: " + correct.summary());
        System.out.println("[RAFT] 무작위 장애 시드 " + FROM + ".." + TO + " 투표 제한 제거(불변식 전부): " + broken.summary()
                + ", 첫 위반 시드 " + broken.report().firstViolationSeeds());
        System.out.println("[RAFT] 무작위 장애 시드 " + FROM + ".." + TO + " 투표 제한 제거(State Machine Safety만): 위반 "
                + brokenSmsOnly.report().violations() + "/" + TO + ", 첫 위반 시드 " + brokenSmsOnly.report().firstViolationSeeds());
        assertThat(correct.report().violations()).isZero();
        assertThat(broken.report().violations()).isPositive();
    }

    @Test
    @DisplayName("무작위 장애: 이전 임기 직접 커밋 변형의 위반 시드 수 (1,000개, 확장 20,000개)")
    void randomDirectCommit() {
        Sweep headline = sweep(FROM, TO, seed -> randomFaultRun(seed, DirectCommitNode::new));
        System.out.println("[RAFT] 무작위 장애 시드 " + FROM + ".." + TO + " 이전 임기 직접 커밋: " + headline.summary()
                + ", 첫 위반 시드 " + headline.report().firstViolationSeeds());

        // 1,000개에서 위반이 안 나오는 것이 이 변형의 실제 성질이다. 조건은 그대로 두고 시드만 늘린다.
        int extendedTo = 20_000;
        Sweep extended = sweep(FROM, extendedTo, seed -> randomFaultRun(seed, DirectCommitNode::new));
        Sweep extendedCorrect = sweep(FROM, extendedTo, seed -> randomFaultRun(seed, RaftNode::new));
        System.out.println("[RAFT] 무작위 장애 시드 " + FROM + ".." + extendedTo + "(조건 동일, 시드만 확장) 이전 임기 직접 커밋: " + extended.summary()
                + ", 첫 위반 시드 " + extended.report().firstViolationSeeds());
        System.out.println("[RAFT] 무작위 장애 시드 " + FROM + ".." + extendedTo + "(조건 동일, 시드만 확장) 정상: " + extendedCorrect.summary());
        assertThat(extendedCorrect.report().violations()).isZero();
        assertThat(extended.report().violations()).isPositive();

        long seed = extended.report().firstViolationSeeds().get(0);
        Run replay = randomFaultRun(seed, DirectCommitNode::new);
        SimulationResult result = replay.sim().run();
        String message = result.violation().message();
        System.out.println("[RAFT] 직접 커밋 첫 위반 시드 " + seed + " 재현: t=" + result.violation().time() + " "
                + (message.length() > 120 ? message.substring(0, 120) + "..." : message));
        assertThat(result.reason()).isEqualTo(StopReason.INVARIANT_VIOLATED);
    }
}
