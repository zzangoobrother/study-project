package com.example.consensus.raft;

import com.example.consensus.raft.RaftMessage.AppendEntries;
import com.example.consensus.raft.RaftMessage.AppendResponse;
import com.example.consensus.raft.RaftMessage.RequestVote;
import com.example.consensus.raft.RaftMessage.VoteResponse;
import com.example.consensus.sim.Context;
import com.example.consensus.sim.Node;
import com.example.consensus.sim.NodeId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 논문 그림 2의 Raft 서버 한 대. 팔로워, 후보, 리더 역할을 한 클래스가 모두 맡는다.
 * 멤버 변경, 스냅샷, 클라이언트 세션은 다루지 않는다.
 *
 * <h2>상태</h2>
 * <ul>
 *   <li>영속(Storage): currentTerm, votedFor, log. 응답을 보내기 전에 반드시 먼저 기록한다.</li>
 *   <li>휘발: commitIndex, lastApplied, 상태 머신(applied), 후보의 득표, 리더의 nextIndex/matchIndex.
 *       재시작하면 새 객체가 만들어지므로 자연스럽게 초기화된다.</li>
 * </ul>
 *
 * <h2>타이머</h2>
 * 취소 기능이 없으므로 키에 세대를 담아 오래된 타이머를 무시한다.
 * 선거 타임아웃은 {@code ElectionTimeout(epoch)}로 "가장 최근에 건 것"만 유효하고,
 * 하트비트는 {@code Heartbeat(term)}으로 "그 임기의 리더"일 때만 유효하다.
 *
 * <p>반례 변형({@code raft.broken})은 규칙 한 줄만 바꾸도록 보호된 메서드 두 개
 * ({@link #candidateLogIsUpToDate}, {@link #mayCommit})를 덮어쓴다.
 */
public class RaftNode implements Node {

    private record ElectionTimeout(long epoch) {}

    private record Heartbeat(long term) {}

    private static final String TERM_KEY = "raft.currentTerm";
    private static final String VOTED_FOR_KEY = "raft.votedFor";
    private static final String LOG_KEY = "raft.log";

    private final RaftConfig config;
    private final RaftMonitor monitor;
    private final RaftWorkload workload;

    /** onStart에서 받은 Context. 테스트가 바깥에서 propose/campaign을 호출할 때 쓴다(세대가 바뀌면 이 노드 객체째 버려진다). */
    private Context ctx;

    // 영속 상태
    private long currentTerm;
    private NodeId votedFor;
    private List<LogEntry> log; // 불변 리스트. 바꿀 때마다 통째로 교체해 저장한다.

    // 휘발 상태
    private Role role;
    private int commitIndex;
    private int lastApplied;
    private final List<String> applied = new ArrayList<>(); // 상태 머신: 적용한 명령을 순서대로 기록한다.
    private long electionEpoch;
    private final Set<NodeId> votes = new TreeSet<>();
    private final Map<NodeId, Integer> nextIndex = new TreeMap<>();
    private final Map<NodeId, Integer> matchIndex = new TreeMap<>();

    public RaftNode(RaftConfig config, RaftMonitor monitor, RaftWorkload workload) {
        this.config = config;
        this.monitor = monitor;
        this.workload = workload;
    }

    // ---- 시작과 재시작 ----

    @Override
    public void onStart(Context ctx) {
        this.ctx = ctx;
        currentTerm = ctx.storage().getOrDefault(TERM_KEY, 0L);
        votedFor = (NodeId) ctx.storage().get(VOTED_FOR_KEY);
        log = ctx.storage().<List<LogEntry>>getOrDefault(LOG_KEY, List.of());
        role = Role.FOLLOWER;
        commitIndex = 0;
        lastApplied = 0;
        resetElectionTimer(ctx);
    }

    @Override
    public void onMessage(Context ctx, NodeId from, Object message) {
        switch ((RaftMessage) message) {
            case RequestVote m -> onRequestVote(ctx, from, m);
            case VoteResponse m -> onVoteResponse(ctx, from, m);
            case AppendEntries m -> onAppendEntries(ctx, from, m);
            case AppendResponse m -> onAppendResponse(ctx, from, m);
        }
    }

    @Override
    public void onTimer(Context ctx, Object timerKey) {
        switch (timerKey) {
            case ElectionTimeout t -> {
                // 가장 최근에 건 타이머만 유효하다. 리더는 선거 타이머를 쓰지 않는다.
                if (t.epoch() == electionEpoch && role != Role.LEADER) {
                    startElection(ctx);
                }
            }
            case Heartbeat h -> {
                if (role == Role.LEADER && h.term() == currentTerm) {
                    fillFromWorkload(ctx);
                    replicateToAll(ctx);
                    ctx.setTimer(config.heartbeatInterval(), h);
                }
            }
            default -> throw new IllegalArgumentException("알 수 없는 타이머: " + timerKey);
        }
    }

    // ---- 선거 ----

    private void startElection(Context ctx) {
        role = Role.CANDIDATE;
        currentTerm++;
        votedFor = ctx.self(); // 자기 자신에게 투표
        saveTermAndVote(ctx);
        votes.clear();
        votes.add(ctx.self());
        monitor.electionStarted(ctx.self(), currentTerm);
        resetElectionTimer(ctx);
        ctx.broadcast(new RequestVote(currentTerm, lastLogIndex(), lastLogTerm()));
        if (votes.size() >= quorum(ctx)) { // 노드가 한 대뿐인 클러스터
            becomeLeader(ctx);
        }
    }

    private void onRequestVote(Context ctx, NodeId from, RequestVote m) {
        if (m.term() > currentTerm) {
            becomeFollower(ctx, m.term());
        }
        boolean grant = m.term() == currentTerm                                    // 옛 임기의 요청은 거절
                && (votedFor == null || votedFor.equals(from))                     // 임기당 한 표
                && candidateLogIsUpToDate(m.lastLogTerm(), m.lastLogIndex());      // 투표 제한
        if (grant) {
            votedFor = from;
            saveTermAndVote(ctx); // 표를 던졌다는 사실을 응답 전에 남긴다. 잊으면 같은 임기에 두 번 투표할 수 있다.
            resetElectionTimer(ctx);
        }
        ctx.send(from, new VoteResponse(currentTerm, grant));
    }

    /**
     * 투표 제한(논문 5.4.1): 후보의 로그가 내 로그만큼 최신일 때만 투표한다.
     * 마지막 엔트리의 term이 더 크면 최신이고, term이 같으면 더 긴 로그가 최신이다.
     */
    protected boolean candidateLogIsUpToDate(long candidateLastLogTerm, int candidateLastLogIndex) {
        if (candidateLastLogTerm != lastLogTerm()) {
            return candidateLastLogTerm > lastLogTerm();
        }
        return candidateLastLogIndex >= lastLogIndex();
    }

    private void onVoteResponse(Context ctx, NodeId from, VoteResponse m) {
        if (m.term() > currentTerm) {
            becomeFollower(ctx, m.term());
            return;
        }
        // 지금 선거(같은 임기)에 대한 찬성표만 센다. 늦게 도착한 옛 임기 응답은 버린다.
        if (role != Role.CANDIDATE || m.term() != currentTerm || !m.granted()) {
            return;
        }
        votes.add(from);
        if (votes.size() >= quorum(ctx)) {
            becomeLeader(ctx);
        }
    }

    private void becomeLeader(Context ctx) {
        role = Role.LEADER;
        electionEpoch++; // 남아 있는 선거 타이머를 모두 무효로 만든다.
        nextIndex.clear();
        matchIndex.clear();
        for (NodeId peer : ctx.peers()) {
            nextIndex.put(peer, log.size() + 1); // 낙관적으로 "다 가지고 있을 것"이라 가정하고 시작한다.
            matchIndex.put(peer, 0);
        }
        monitor.becameLeader(ctx.now(), ctx.self(), currentTerm, log);
        replicateToAll(ctx); // 당선 직후 즉시 하트비트로 권위를 알린다.
        ctx.setTimer(config.heartbeatInterval(), new Heartbeat(currentTerm));
    }

    /** 더 높은(또는 같은) 임기의 리더·후보를 만났을 때 팔로워로 내려간다. */
    private void becomeFollower(Context ctx, long term) {
        boolean wasLeader = role == Role.LEADER;
        if (term > currentTerm) {
            currentTerm = term;
            votedFor = null; // 새 임기에는 아직 아무에게도 투표하지 않았다.
            saveTermAndVote(ctx);
        }
        role = Role.FOLLOWER;
        if (wasLeader) {
            resetElectionTimer(ctx); // 리더는 선거 타이머가 꺼져 있었으므로 다시 걸어야 한다.
        }
    }

    // ---- 로그 복제: 팔로워 쪽 ----

    private void onAppendEntries(Context ctx, NodeId from, AppendEntries m) {
        if (m.term() < currentTerm) {
            ctx.send(from, new AppendResponse(currentTerm, false, m.term(), m.prevLogIndex(), 0));
            return;
        }
        becomeFollower(ctx, m.term()); // 같은 임기의 후보도 리더가 있음을 알면 내려온다.
        resetElectionTimer(ctx);       // 현재 리더의 신호이므로 선거 타이머를 다시 건다.

        if (!hasEntryAt(m.prevLogIndex(), m.prevLogTerm())) {
            ctx.send(from, new AppendResponse(currentTerm, false, m.term(), m.prevLogIndex(), 0));
            return;
        }
        List<LogEntry> merged = mergeEntries(m.prevLogIndex(), m.entries());
        if (merged != log) {
            saveLog(ctx, merged); // 로그를 디스크에 쓴 뒤에 성공이라고 답한다.
        }
        int lastNewIndex = m.prevLogIndex() + m.entries().size();
        if (m.leaderCommit() > commitIndex) {
            advanceCommitIndex(ctx, Math.min(m.leaderCommit(), lastNewIndex));
        }
        ctx.send(from, new AppendResponse(currentTerm, true, m.term(), m.prevLogIndex(), m.entries().size()));
    }

    /** prevLogIndex 위치에 prevLogTerm인 엔트리가 있는가. prevLogIndex == 0이면 항상 참이다. */
    private boolean hasEntryAt(int index, long term) {
        return index == 0 || (index <= log.size() && log.get(index - 1).term() == term);
    }

    /**
     * 로그에 entries를 prevLogIndex 뒤에 합친다. 이미 같은 엔트리가 있으면 건드리지 않고,
     * term이 다른 엔트리를 처음 만난 지점부터는 내 로그를 잘라내고 리더의 엔트리로 덮어쓴다.
     * 바뀐 게 없으면 같은 인스턴스를 돌려준다(지연·중복 메시지가 로그를 줄이지 않도록,
     * 충돌이 없으면 내 로그 뒤쪽을 자르지 않는다).
     */
    private List<LogEntry> mergeEntries(int prevLogIndex, List<LogEntry> entries) {
        for (int i = 0; i < entries.size(); i++) {
            int position = prevLogIndex + i; // 0부터 센 로그 위치
            if (position >= log.size() || log.get(position).term() != entries.get(i).term()) {
                List<LogEntry> merged = new ArrayList<>(log.subList(0, position));
                merged.addAll(entries.subList(i, entries.size()));
                return List.copyOf(merged);
            }
        }
        return log;
    }

    // ---- 로그 복제: 리더 쪽 ----

    private void replicateToAll(Context ctx) {
        for (NodeId peer : ctx.peers()) {
            sendAppendEntries(ctx, peer);
        }
    }

    /** nextIndex[peer]부터 끝까지 보낸다. 앞 엔트리 정보(prev)는 팔로워의 일치 검사용이다. */
    private void sendAppendEntries(Context ctx, NodeId peer) {
        int prevLogIndex = nextIndex.get(peer) - 1;
        long prevLogTerm = prevLogIndex == 0 ? 0 : log.get(prevLogIndex - 1).term();
        List<LogEntry> entries = log.subList(prevLogIndex, log.size()); // subList of immutable list: 불변
        ctx.send(peer, new AppendEntries(currentTerm, prevLogIndex, prevLogTerm, entries, commitIndex));
    }

    private void onAppendResponse(Context ctx, NodeId from, AppendResponse m) {
        if (m.term() > currentTerm) {
            becomeFollower(ctx, m.term());
            return;
        }
        // 이번 임기에 보낸 요청에 대한 응답만 다룬다(옛 임기 요청에 대한 늦은 응답은 버린다).
        if (role != Role.LEADER || m.requestTerm() != currentTerm) {
            return;
        }
        if (m.success()) {
            int match = m.prevLogIndex() + m.entryCount();
            matchIndex.merge(from, match, Math::max);
            nextIndex.merge(from, match + 1, Math::max);
            advanceLeaderCommit(ctx);
        } else if (nextIndex.get(from) == m.prevLogIndex() + 1) {
            // 가장 최근 요청에 대한 거절일 때만 한 칸 물려 바로 다시 보낸다. 지연·중복된 옛 거절은 무시한다.
            nextIndex.put(from, m.prevLogIndex());
            monitor.nextIndexDecreased(from);
            sendAppendEntries(ctx, from);
        }
    }

    /**
     * 커밋 규칙(논문 5.4.2): 과반 서버가 N 이상을 복제했고 log[N].term == currentTerm인 가장 큰 N까지 커밋한다.
     * 이전 임기의 엔트리는 직접 세지 않고, 현재 임기 엔트리가 커밋될 때 함께 간접 커밋된다.
     */
    private void advanceLeaderCommit(Context ctx) {
        for (int n = log.size(); n > commitIndex; n--) {
            if (mayCommit(n) && replicatedOnMajority(n, ctx)) {
                advanceCommitIndex(ctx, n);
                return;
            }
        }
    }

    /** index를 커밋해도 되는가. 정상 구현은 "현재 임기에 만든 엔트리"만 허용한다. */
    protected boolean mayCommit(int index) {
        return log.get(index - 1).term() == currentTerm;
    }

    private boolean replicatedOnMajority(int index, Context ctx) {
        int count = 1; // 리더 자신
        for (NodeId peer : ctx.peers()) {
            if (matchIndex.get(peer) >= index) {
                count++;
            }
        }
        return count >= quorum(ctx);
    }

    private void fillFromWorkload(Context ctx) {
        if (workload == null) {
            return;
        }
        // 이번 임기에 만든 미커밋 엔트리가 모자라면 채운다. 이전 임기의 미커밋 엔트리는 세지 않는다:
        // 그것들은 이번 임기 엔트리가 커밋될 때에야 함께 커밋되므로, 세어 버리면 새 리더가 영영 커밋을 못 한다.
        while (uncommittedOfCurrentTerm() < workload.maxOutstanding()) {
            String command = workload.issue();
            if (command == null) {
                return;
            }
            appendToOwnLog(ctx, command);
        }
    }

    private int uncommittedOfCurrentTerm() {
        int count = 0;
        for (int index = log.size(); index > commitIndex && log.get(index - 1).term() == currentTerm; index--) {
            count++;
        }
        return count;
    }

    private void appendToOwnLog(Context ctx, String command) {
        List<LogEntry> next = new ArrayList<>(log);
        next.add(new LogEntry(currentTerm, command));
        saveLog(ctx, List.copyOf(next)); // 내 로그에 먼저 쓰고 나서 보낸다.
        advanceLeaderCommit(ctx);        // 서버가 한 대뿐이면 여기서 바로 과반이다.
    }

    // ---- 커밋과 적용 ----

    private void advanceCommitIndex(Context ctx, int newCommitIndex) {
        if (newCommitIndex <= commitIndex) {
            return;
        }
        int old = commitIndex;
        commitIndex = newCommitIndex;
        monitor.commitAdvanced(ctx.self(), old, newCommitIndex, log);
        applyCommitted(ctx);
    }

    /** commitIndex까지 순서대로 상태 머신에 적용한다. */
    private void applyCommitted(Context ctx) {
        // lastApplied < log.size() 조건은 정상 구현에서는 항상 참이다.
        // 커밋된 엔트리를 덮어쓰는 반례 변형에서 commitIndex가 로그보다 길어져도 범위를 벗어나지 않게 하는 방어다.
        while (lastApplied < commitIndex && lastApplied < log.size()) {
            lastApplied++;
            String command = log.get(lastApplied - 1).command();
            applied.add(command);
            monitor.applied(ctx.now(), ctx.self(), lastApplied, command);
        }
    }

    // ---- 클라이언트와 테스트용 진입점 ----

    /**
     * 클라이언트 명령을 받는다. 리더가 아니면 거절(false)한다.
     * 리더면 자기 로그에 붙이고 즉시 팔로워들에게 복제를 시작한다. 커밋은 과반이 복제한 뒤에야 일어난다.
     */
    public boolean propose(String command) {
        if (role != Role.LEADER) {
            return false;
        }
        appendToOwnLog(ctx, command);
        replicateToAll(ctx);
        return true;
    }

    /** 선거 타임아웃을 기다리지 않고 지금 후보가 된다. 크래시·분할 스크립트로 선거 순서를 정확히 짜는 테스트용이다. */
    public void campaign() {
        if (role != Role.LEADER) {
            startElection(ctx);
        }
    }

    public Role role() {
        return role;
    }

    public long currentTerm() {
        return currentTerm;
    }

    public List<LogEntry> log() {
        return log;
    }

    public int commitIndex() {
        return commitIndex;
    }

    /** 상태 머신이 적용한 명령 목록(적용 순서). */
    public List<String> appliedCommands() {
        return List.copyOf(applied);
    }

    // ---- 영속화와 보조 ----

    private void saveTermAndVote(Context ctx) {
        ctx.storage().put(TERM_KEY, currentTerm);
        ctx.storage().put(VOTED_FOR_KEY, votedFor);
    }

    private void saveLog(Context ctx, List<LogEntry> newLog) {
        log = newLog;
        ctx.storage().put(LOG_KEY, newLog);
        monitor.logChanged(ctx.self(), newLog);
    }

    private void resetElectionTimer(Context ctx) {
        electionEpoch++;
        long min = config.electionTimeoutMin();
        long max = config.electionTimeoutMax();
        long timeout = min == max ? min : min + ctx.random().nextLong(max - min + 1);
        ctx.setTimer(timeout, new ElectionTimeout(electionEpoch));
    }

    private int lastLogIndex() {
        return log.size();
    }

    private long lastLogTerm() {
        return log.isEmpty() ? 0 : log.get(log.size() - 1).term();
    }

    private static int quorum(Context ctx) {
        return (ctx.peers().size() + 1) / 2 + 1;
    }
}
