package com.example.consensus.raft;

import static com.example.consensus.raft.RaftScenarios.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.consensus.flp.RandomScheduler;
import com.example.consensus.sim.Event;
import com.example.consensus.sim.ReliableNetwork;
import com.example.consensus.sim.Scheduler;
import com.example.consensus.sim.SimulationBuilder;
import com.example.consensus.sim.SimulationResult;
import com.example.consensus.sim.StopReason;
import com.example.consensus.sim.trace.TraceEntry;
import java.util.List;
import java.util.Locale;
import java.util.function.LongFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 이벤트 꺼내는 순서를 바꿔도 안전성은 깨지지 않는다. 진행(liveness)은 순서에 달려 있다.
 * 시간순 스케줄러를 기준으로, 시각을 무시하는 FIFO(생성 순서)와 무작위 순서를 같은 조건에서 돌린다.
 * 조건: 5대, ReliableNetwork(1,10), 장애 없음, 선거 타임아웃 [150,300], 하트비트 50, 부하 명령 10개(미커밋 3개까지).
 * 목표: 5대 모두 명령 10개를 적용(= 리더가 안정되어 커밋이 이어짐).
 */
class RaftAdversarialOrderTest {

    private static final int SEEDS = 50;
    private static final int COMMANDS = 10;
    private static final long MAX_STEPS = 20_000;

    /** 시각을 무시하고 먼저 생긴 이벤트(seq가 작은 것)부터 꺼낸다. flp 모듈의 FIFO 기준선과 같다. */
    private static final Scheduler FIFO = (pending, view) -> {
        Event best = pending.get(0);
        for (Event e : pending) {
            if (e.seq() < best.seq()) {
                best = e;
            }
        }
        return best;
    };

    private record Outcome(int reachedGoal, int everHadLeader, double avgTerms, double avgMaxTerm, double avgStepsToGoal, int violations,
                           double avgCommitted, int seedsWithCommit) {}

    private Outcome run(LongFunction<Scheduler> scheduler) {
        int reached = 0, hadLeader = 0, violations = 0;
        double terms = 0, maxTerm = 0, steps = 0, committed = 0;
        int withCommit = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            RaftMonitor monitor = new RaftMonitor();
            SimulationBuilder b = base(seed, RaftNode::new, RaftConfig.of(), new RaftWorkload(COMMANDS, 3), monitor, new ReliableNetwork(1, 10))
                    .stopWhen(view -> allApplied(view, COMMANDS))
                    .maxSteps(MAX_STEPS);
            if (scheduler != null) {
                b.scheduler(scheduler.apply(seed));
            }
            SimulationResult r = b.build().run();
            violations += r.reason() == StopReason.INVARIANT_VIOLATED ? 1 : 0;
            if (r.reason() == StopReason.STOP_CONDITION) {
                reached++;
                steps += r.steps();
            }
            committed += monitor.committedCount();
            withCommit += monitor.committedCount() > 0 ? 1 : 0;
            hadLeader += monitor.leaderOfTerm().isEmpty() ? 0 : 1;
            terms += monitor.leaderOfTerm().size();
            maxTerm += monitor.leaderOfTerm().isEmpty() ? 0 : java.util.Collections.max(monitor.leaderOfTerm().keySet());
        }
        return new Outcome(reached, hadLeader, terms / SEEDS, maxTerm / SEEDS, reached == 0 ? Double.NaN : steps / reached, violations, committed / SEEDS, withCommit);
    }

    private void print(String label, Outcome o) {
        System.out.println(String.format(Locale.ROOT,
                "[RAFT] 적대 순서 %s (시드 1..%d, maxSteps %d): 5대 모두 명령 %d개 적용 %d/%d, 리더가 한 번이라도 뽑힌 시드 %d, "
                        + "시드당 리더 임기 수 평균 %.2f(마지막 리더 임기 평균 %.1f), 커밋이 하나라도 있었던 시드 %d(평균 커밋 index %.2f개), "
                        + "목표 도달 평균 step %.0f, 불변식 위반 %d",
                label, SEEDS, MAX_STEPS, COMMANDS, o.reachedGoal(), SEEDS, o.everHadLeader(), o.avgTerms(), o.avgMaxTerm(),
                o.seedsWithCommit(), o.avgCommitted(), o.avgStepsToGoal(), o.violations()));
    }

    @Test
    @DisplayName("시간순 / FIFO / 무작위 순서: 안전성은 지키고, 진행은 순서에 달려 있다")
    void schedulerComparison() {
        Outcome timeOrder = run(null);
        Outcome fifo = run(seed -> FIFO);
        Outcome random = run(RandomScheduler::new);
        print("시간순(기준)", timeOrder);
        print("FIFO(시각 무시, 생성 순서)", fifo);
        print("무작위 순서(flp RandomScheduler)", random);

        assertThat(timeOrder.violations() + fifo.violations() + random.violations()).isZero();
        assertThat(timeOrder.reachedGoal()).isEqualTo(SEEDS);
    }

    @Test
    @DisplayName("FIFO에서 진행이 멈추는 이유: 선거 타이머가 그 선거의 투표 응답보다 먼저 꺼내진다")
    void whyFifoStalls() {
        RaftMonitor monitor = new RaftMonitor();
        SimulationResult r = base(1, RaftNode::new, RaftConfig.of(), new RaftWorkload(COMMANDS, 3), monitor, new ReliableNetwork(1, 10))
                .scheduler(FIFO).maxSteps(2_000).build().run();

        // 처음 몇 개의 타이머/배달 사건을 순서대로 보인다.
        List<TraceEntry> events = r.trace().entries().stream()
                .filter(e -> e instanceof TraceEntry.TimerFired || e instanceof TraceEntry.Deliver)
                .toList();
        StringBuilder first = new StringBuilder();
        for (int i = 0; i < 24 && i < events.size(); i++) {
            first.append(describe(events.get(i))).append(" | ");
        }
        System.out.println("[RAFT] FIFO 처음 24개 사건: " + first);

        // 이긴 후보 없이 임기만 올라가는지, 투표 응답이 후보에게 닿았을 때 후보가 이미 다음 임기인지 센다.
        int voteResponses = 0, staleVoteResponses = 0, grantedVotes = 0;
        for (TraceEntry e : r.trace().entries()) {
            if (e instanceof TraceEntry.Deliver d && d.message() instanceof RaftMessage.VoteResponse v) {
                voteResponses++;
                grantedVotes += v.granted() ? 1 : 0;
            }
        }
        long maxTerm = r.trace().entries().stream()
                .filter(e -> e instanceof TraceEntry.Send s && s.message() instanceof RaftMessage.RequestVote)
                .mapToLong(e -> ((RaftMessage.RequestVote) ((TraceEntry.Send) e).message()).term()).max().orElse(0);
        System.out.println("[RAFT] FIFO 2,000 step: 리더 임기 수 " + monitor.leaderOfTerm().size() + ", RequestVote가 올린 최대 임기 " + maxTerm
                + ", 후보 등장 임기 수 " + monitor.termsWithCandidates() + ", 전달된 VoteResponse " + voteResponses + "개 중 찬성 " + grantedVotes + "개");
        assertThat(r.reason()).isNotEqualTo(StopReason.INVARIANT_VIOLATED);
    }

    @Test
    @DisplayName("무작위 순서에서 step을 15배(300,000)로 늘려도 커밋이 없는 이유: 타이머가 메시지만큼 자주 뽑힌다")
    void randomOrderLongRun() {
        int seeds = 5;
        double leaders = 0, elections = 0, committed = 0, pendingSum = 0, steps = 0;
        int violations = 0;
        for (long seed = 1; seed <= seeds; seed++) {
            RaftMonitor monitor = new RaftMonitor();
            long[] pending = {0};
            SimulationResult r = base(seed, RaftNode::new, RaftConfig.of(), new RaftWorkload(COMMANDS, 3), monitor, new ReliableNetwork(1, 10))
                    .scheduler(new RandomScheduler(seed))
                    .stopWhen(view -> {
                        pending[0] += view.pending().size();
                        return allApplied(view, COMMANDS);
                    })
                    .maxSteps(300_000).build().run();
            violations += r.reason() == StopReason.INVARIANT_VIOLATED ? 1 : 0;
            leaders += monitor.leaderOfTerm().size();
            elections += monitor.electionsStarted();
            committed += monitor.committedCount();
            pendingSum += (double) pending[0] / Math.max(r.steps(), 1);
            steps += r.steps();
        }
        System.out.println(String.format(Locale.ROOT,
                "[RAFT] 무작위 순서 장기 실행(시드 1..%d, maxSteps 300000): 시드당 리더 당선 평균 %.0f회, 선거 시작 평균 %.0f회(선거 한 번에 약 %.1f step), "
                        + "커밋된 index 평균 %.1f개, 대기 이벤트 수 평균 %.1f개, 불변식 위반 %d",
                seeds, leaders / seeds, elections / seeds, steps / elections, committed / seeds, pendingSum / seeds, violations));
        assertThat(violations).isZero();
    }

    private static String describe(TraceEntry e) {
        return switch (e) {
            case TraceEntry.TimerFired t -> t.node() + ":" + t.key().toString().replace("ElectionTimeout", "선거타이머").replace("Heartbeat", "하트비트");
            case TraceEntry.Deliver d -> d.from() + "->" + d.to() + ":" + d.message().getClass().getSimpleName() + termOf(d.message());
            default -> e.toString();
        };
    }

    private static String termOf(Object m) {
        return switch ((RaftMessage) m) {
            case RaftMessage.RequestVote v -> "(t" + v.term() + ")";
            case RaftMessage.VoteResponse v -> "(t" + v.term() + (v.granted() ? ",찬성" : ",반대") + ")";
            case RaftMessage.AppendEntries a -> "(t" + a.term() + ")";
            case RaftMessage.AppendResponse a -> "(t" + a.term() + ")";
        };
    }
}
