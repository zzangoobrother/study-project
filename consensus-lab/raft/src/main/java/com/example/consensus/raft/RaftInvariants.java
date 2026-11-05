package com.example.consensus.raft;

import com.example.consensus.sim.Invariant;
import com.example.consensus.sim.InvariantViolation;
import com.example.consensus.sim.NodeId;
import java.util.List;

/**
 * Raft 논문 그림 3의 안전성 성질을 매 이벤트 후 검사하는 Invariant 모음.
 * 이력이 필요한 성질은 {@link RaftMonitor}가 쌓은 기록을 읽는다.
 * 이름은 위반 메시지의 접두사이자 시드별 위반 분류의 키다.
 */
public final class RaftInvariants {

    public static final String ELECTION_SAFETY = "Election Safety";
    public static final String LOG_MATCHING = "Log Matching";
    public static final String STATE_MACHINE_SAFETY = "State Machine Safety";
    public static final String LEADER_COMPLETENESS = "Leader Completeness";

    private RaftInvariants() {}

    /** 네 가지를 모두. 시뮬레이션 하나마다 새로 만들어야 한다(Log Matching이 검사 이력을 가진다). */
    public static List<Invariant> all(RaftMonitor monitor) {
        return List.of(electionSafety(monitor), logMatching(monitor), stateMachineSafety(monitor), leaderCompleteness(monitor));
    }

    /** 한 term에 리더는 최대 하나다(시뮬레이션 전체 이력 기준). */
    public static Invariant electionSafety(RaftMonitor monitor) {
        return view -> fail(monitor, ELECTION_SAFETY, monitor.electionSafetyViolation());
    }

    /**
     * 두 로그가 같은 index에 같은 term의 엔트리를 가지면, 그 index까지(포함) 로그가 완전히 같다.
     * 크래시한 서버의 디스크 로그까지 비교한다. 로그가 바뀐 때만 다시 검사한다.
     */
    public static Invariant logMatching(RaftMonitor monitor) {
        long[] checkedVersion = {-1};
        return view -> {
            if (monitor.logVersion() == checkedVersion[0]) {
                return;
            }
            checkedVersion[0] = monitor.logVersion();
            List<NodeId> nodes = view.nodes();
            for (int i = 0; i < nodes.size(); i++) {
                for (int j = i + 1; j < nodes.size(); j++) {
                    String problem = firstMismatch(nodes.get(i), monitor.logOf(nodes.get(i)), nodes.get(j), monitor.logOf(nodes.get(j)));
                    fail(monitor, LOG_MATCHING, problem);
                }
            }
        };
    }

    /** 같은 term인 가장 높은 index를 찾아, 거기까지의 엔트리가 모두 같은지 본다(더 낮은 index는 그 안에 포함된다). */
    private static String firstMismatch(NodeId a, List<LogEntry> logA, NodeId b, List<LogEntry> logB) {
        for (int i = Math.min(logA.size(), logB.size()) - 1; i >= 0; i--) {
            if (logA.get(i).term() != logB.get(i).term()) {
                continue;
            }
            for (int k = 0; k <= i; k++) {
                if (!logA.get(k).equals(logB.get(k))) {
                    return a + "와 " + b + "의 index " + (i + 1) + "은 term이 같은데 index " + (k + 1) + "이 다르다: "
                            + logA.get(k) + " vs " + logB.get(k);
                }
            }
            return null;
        }
        return null;
    }

    /** 서버들이 적용한 명령 목록은 서로의 접두사다 = 같은 index에 다른 명령을 적용한 서버가 없다(커밋 충돌 포함). */
    public static Invariant stateMachineSafety(RaftMonitor monitor) {
        return view -> fail(monitor, STATE_MACHINE_SAFETY, monitor.stateMachineViolation());
    }

    /** 어떤 서버에서 커밋된 엔트리는 이후 당선되는 모든 리더의 로그에 있다. */
    public static Invariant leaderCompleteness(RaftMonitor monitor) {
        return view -> fail(monitor, LEADER_COMPLETENESS, monitor.leaderCompletenessViolation());
    }

    private static void fail(RaftMonitor monitor, String name, String problem) {
        if (problem != null) {
            monitor.markDetected(name);
            throw new InvariantViolation(name + " 위반: " + problem);
        }
    }
}
