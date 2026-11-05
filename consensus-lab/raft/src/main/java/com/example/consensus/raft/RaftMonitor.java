package com.example.consensus.raft;

import com.example.consensus.sim.NodeId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Set;

/**
 * 시뮬레이션 하나에 하나씩 만드는 관찰자. 노드들이 중요한 사건을 여기에 보고하고,
 * {@link RaftInvariants}와 테스트가 여기서 전체 이력을 읽는다.
 *
 * <p>왜 필요한가: 불변식은 "크래시한 서버가 과거에 적용한 명령", "옛 임기의 리더"처럼 지금은 사라진 사실까지 봐야 한다.
 * SimulationView는 살아 있는 노드만 보여 주므로 이력은 따로 쌓는다. 노드의 동작에는 어떤 영향도 주지 않는다(보고만 받는다).
 * 단일 스레드 시뮬레이터 안에서만 쓰이므로 동기화는 없다. 위반은 예외로 던지지 않고 기록만 한다(콜백 안에서 던지면
 * 시뮬레이터가 잡지 못한다). 던지는 일은 Invariant가 한다.
 */
public final class RaftMonitor {

    /** 리더 당선 기록 한 건. */
    public record LeaderEvent(long time, NodeId node, long term) {}

    private final Map<Long, NodeId> leaderOfTerm = new TreeMap<>();
    private final List<LeaderEvent> leaderEvents = new ArrayList<>();
    private final Map<Long, Integer> candidaciesOfTerm = new TreeMap<>();
    private final Map<NodeId, List<LogEntry>> logs = new TreeMap<>();
    private final Map<Integer, LogEntry> committed = new TreeMap<>();
    private final Map<Integer, String> appliedHistory = new TreeMap<>();
    private final Map<NodeId, Map<Integer, Long>> appliedTime = new TreeMap<>();
    private final Map<NodeId, Integer> decrements = new TreeMap<>();

    private long logVersion = 0;
    private int electionsStarted = 0;
    private int totalDecrements = 0;

    private String electionSafetyViolation;
    private String leaderCompletenessViolation;
    private String stateMachineViolation;
    private String detectedInvariant;

    // ---- 노드가 보고하는 사건 ----

    void electionStarted(NodeId node, long term) {
        electionsStarted++;
        candidaciesOfTerm.merge(term, 1, Integer::sum);
    }

    /**
     * 당선 보고. Election Safety(한 term에 리더 하나)와 Leader Completeness
     * (이미 커밋된 엔트리가 새 리더의 로그에 모두 있다)를 이 시점에 검사해 둔다.
     */
    void becameLeader(long time, NodeId node, long term, List<LogEntry> log) {
        NodeId previous = leaderOfTerm.putIfAbsent(term, node);
        if (previous != null && electionSafetyViolation == null) {
            electionSafetyViolation = "term " + term + "의 리더가 둘이다: " + previous + ", " + node;
        }
        leaderEvents.add(new LeaderEvent(time, node, term));
        if (leaderCompletenessViolation == null) {
            for (Map.Entry<Integer, LogEntry> c : committed.entrySet()) {
                int index = c.getKey();
                if (index > log.size() || !log.get(index - 1).equals(c.getValue())) {
                    leaderCompletenessViolation = "term " + term + "의 리더 " + node + "의 로그 " + log
                            + "에 이미 커밋된 index " + index + " " + c.getValue() + "이 없다";
                    break;
                }
            }
        }
    }

    void logChanged(NodeId node, List<LogEntry> log) {
        logs.put(node, log);
        logVersion++;
    }

    /** commitIndex가 from에서 to로 올랐다. (from, to] 구간이 "커밋됨"으로 기록된다. */
    void commitAdvanced(NodeId node, int from, int to, List<LogEntry> log) {
        for (int index = from + 1; index <= to; index++) {
            LogEntry entry = log.get(index - 1);
            LogEntry known = committed.putIfAbsent(index, entry);
            if (known != null && !known.equals(entry) && stateMachineViolation == null) {
                stateMachineViolation = node + "가 index " + index + "에 " + entry + "을 커밋했지만 이미 " + known + "이 커밋되어 있다";
            }
        }
    }

    /** 상태 머신 적용. 어느 서버든 같은 index에는 같은 명령을 적용해야 한다(State Machine Safety). */
    void applied(long time, NodeId node, int index, String command) {
        String known = appliedHistory.putIfAbsent(index, command);
        if (known != null && !known.equals(command) && stateMachineViolation == null) {
            stateMachineViolation = node + "가 index " + index + "에 " + command + "을 적용했지만 다른 서버는 " + known + "을 적용했다";
        }
        appliedTime.computeIfAbsent(node, n -> new TreeMap<>()).put(index, time);
    }

    void nextIndexDecreased(NodeId follower) {
        totalDecrements++;
        decrements.merge(follower, 1, Integer::sum);
    }

    // ---- 불변식이 읽는 값 ----

    long logVersion() {
        return logVersion;
    }

    String electionSafetyViolation() {
        return electionSafetyViolation;
    }

    String leaderCompletenessViolation() {
        return leaderCompletenessViolation;
    }

    String stateMachineViolation() {
        return stateMachineViolation;
    }

    /** 불변식이 처음 위반을 던졌을 때 그 이름을 남긴다. 위반 시드를 불변식별로 세는 데 쓴다. */
    void markDetected(String invariantName) {
        if (detectedInvariant == null) {
            detectedInvariant = invariantName;
        }
    }

    /** 시뮬레이터에 처음 위반으로 보고된 불변식 이름. 위반이 없었으면 null. */
    public String detectedInvariant() {
        return detectedInvariant;
    }

    // ---- 측정값 ----

    /** 서버가 (크래시 중이어도) 디스크에 가진 로그. 한 번도 쓴 적 없으면 빈 로그. */
    public List<LogEntry> logOf(NodeId node) {
        return logs.getOrDefault(node, List.of());
    }

    public List<LeaderEvent> leaderEvents() {
        return Collections.unmodifiableList(leaderEvents);
    }

    /** 첫 리더가 당선된 가상 시각. 없으면 -1. */
    public long firstLeaderTime() {
        return leaderEvents.isEmpty() ? -1 : leaderEvents.get(0).time();
    }

    public Map<Long, NodeId> leaderOfTerm() {
        return Collections.unmodifiableMap(leaderOfTerm);
    }

    /** 선거를 시작(후보가 됨)한 횟수. 서버마다 센다. */
    public int electionsStarted() {
        return electionsStarted;
    }

    /** 후보가 한 명이라도 나온 term의 수. */
    public int termsWithCandidates() {
        return candidaciesOfTerm.size();
    }

    /** 후보는 있었지만 리더가 뽑히지 못한 term의 수. 표가 갈렸거나 임기 도중 더 높은 term에 밀린 선거다. */
    public int failedTerms() {
        Set<Long> failed = new TreeSet<>(candidaciesOfTerm.keySet());
        failed.removeAll(leaderOfTerm.keySet());
        return failed.size();
    }

    /** 커밋된 index의 개수(서버 누구에게든 한 번이라도 커밋된 서로 다른 index). */
    public int committedCount() {
        return committed.size();
    }

    /** node가 index를 (가장 최근 세대에서) 적용한 가상 시각. 적용한 적 없으면 -1. */
    public long appliedTime(NodeId node, int index) {
        return appliedTime.getOrDefault(node, Map.of()).getOrDefault(index, -1L);
    }

    /** 리더들이 follower에게 보내다 거절당해 nextIndex를 한 칸씩 물린 횟수. */
    public int decrementsFor(NodeId follower) {
        return decrements.getOrDefault(follower, 0);
    }

    public int totalDecrements() {
        return totalDecrements;
    }
}
