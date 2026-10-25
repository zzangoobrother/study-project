package com.example.consensus.flp;

import com.example.consensus.sim.Event;
import com.example.consensus.sim.Scheduler;
import com.example.consensus.sim.SimulationView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * FLP 증명을 코드로 옮긴 적대적 스케줄러. 매 단계, 꺼냈을 때에도 상태가 BIVALENT로 남는 이벤트를 고른다.
 * 두 값 모두 아직 도달 가능한 상태에 머무는 한 아무도 결정할 수 없으므로, 결정은 영원히 미뤄진다.
 *
 * <h2>알고리즘</h2>
 * 대기 이벤트를 "pending에 처음 들어온 step"이 이른 순(오래 기다린 순)으로 정렬해 앞의 maxCandidates개만 후보로 본다.
 * <ol>
 *   <li>마감을 넘긴 이벤트가 없으면: 후보를 오래된 순으로 보며, 꺼낸 뒤에도 BIVALENT인 첫 후보를 고른다.
 *       (지금 상태가 이미 BIVALENT가 아니면 비용을 아끼려고 가장 오래된 후보 하나만 확인한다.)</li>
 *   <li>후보가 모두 탈락하면 위기다. BIVALENT 경로에서 왔다면 판정 오차일 가능성이 높으므로, 프로브를 크게 늘린 판정
 *       ({@link BivalenceProbe#probeDeep})으로 pending 전체를 다시 훑는다(적응형 프로브). 값싼 판정은 한쪽 값으로 가는
 *       무작위 실행이 드문 상태에서 갈림길을 자주 놓친다.</li>
 *   <li>마감(deadline)을 넘긴 이벤트 e가 있으면 e를 먼저 처리해야 한다. 증명 보조정리 2처럼 한다.
 *       <ul>
 *         <li>e를 바로 꺼내도 BIVALENT면 그대로 e를 꺼낸다.</li>
 *         <li>아니면 다른 이벤트 x를 먼저 끼워 넣어 (x 이후도 BIVALENT, x 다음에 e를 꺼내도 BIVALENT)인 x를 찾아 x를 꺼낸다.
 *             e는 다음 단계에서 다시 시도한다. 끼워 넣기는 마감 + insertBudget step까지만 허용한다.</li>
 *       </ul></li>
 *   <li>그래도 BIVALENT를 유지할 수 없으면 가장 오래된(또는 마감을 넘긴) 이벤트를 꺼내고 "적대자 실패"로 기록한다.</li>
 * </ol>
 *
 * <h2>공정성</h2>
 * 적대자는 이벤트를 늦출 수 있지만 영원히 숨길 수는 없다. 이벤트가 마감(deadline) step을 넘기면 위 규칙으로 우선 처리하고,
 * 끼워 넣기 예산(insertBudget)이 바닥나면 무조건 꺼낸다. 따라서 이벤트의 대기 step은 deadline + insertBudget 근처로 묶인다.
 * 실제 최장 대기는 {@link #maxWait()}로 확인한다.
 * <p>
 * 이 모델에서는 타이머도 이벤트라서 "타이머가 메시지보다 먼저 울리는" 순서도 합법이다(지연 상한이 없으므로).
 * <p>
 * 이 스케줄러는 상태를 가진다(꺼낸 이벤트 기록). 시뮬레이션 하나당 새로 만들어야 한다.
 */
public final class AdversarialScheduler implements Scheduler {

    /** 실행 통계. */
    public record Stats(long steps, long maxWait, long failures, long inserts, long probeRuns) {}

    private final BivalenceProbe probe;
    private final int deadline;
    private final int insertBudget;
    private final int maxCandidates;

    /** 지금까지 꺼낸 이벤트의 seq. 이것이 곧 현재 상태를 재현하는 접두사다. */
    private final List<Long> chosen = new ArrayList<>();
    /** seq -> 처음 pending에서 관찰한 step. */
    private final Map<Long, Long> firstSeen = new TreeMap<>();

    private long maxWait = 0;
    private long failures = 0;
    private final List<Integer> failureSteps = new ArrayList<>();
    private long inserts = 0;
    private long escalations = 0;
    /** 직전 단계에서 BIVALENT를 유지했는가(처음에는 true로 시작). */
    private boolean onBivalentPath = true;
    private long maxObservedAge = 0;

    /**
     * @param probe         갈림길 판정기
     * @param deadline      이 step 수만큼 기다린 이벤트는 우선 처리 대상이 된다(D)
     * @param insertBudget  마감을 넘긴 뒤 다른 이벤트를 끼워 넣을 수 있는 추가 step 수
     * @param maxCandidates 한 단계에서 판정해 볼 후보 수 상한(오래된 순)
     */
    public AdversarialScheduler(BivalenceProbe probe, int deadline, int insertBudget, int maxCandidates) {
        this.probe = probe;
        this.deadline = deadline;
        this.insertBudget = insertBudget;
        this.maxCandidates = maxCandidates;
    }

    @Override
    public Event pickNext(List<Event> pending, SimulationView view) {
        long step = view.steps();
        for (Event e : pending) {
            firstSeen.putIfAbsent(e.seq(), step);
        }
        List<Event> byAge = new ArrayList<>(pending);
        byAge.sort(Comparator.comparingLong((Event e) -> firstSeen.get(e.seq())).thenComparingLong(Event::seq));

        Event oldest = byAge.get(0);
        maxObservedAge = Math.max(maxObservedAge, age(oldest, step));
        Event pick = null;
        if (age(oldest, step) >= deadline) {
            pick = pickForOverdue(byAge, oldest, step);
        } else {
            // 이미 BIVALENT가 아닌 상태(적대자가 졌거나 처음부터 한 값뿐)에서는 후보를 다 뒤져 봐야 비용만 든다.
            // 가장 오래된 것 하나만 확인해, 판정 오차로 잠시 놓쳤던 갈림길로 돌아갈 길만 열어 둔다.
            int limit = onBivalentPath ? maxCandidates : 1;
            List<Event> candidates = byAge.subList(0, Math.min(limit, byAge.size()));
            pick = firstBivalent(candidates, false);
            if (pick == null && onBivalentPath) {
                // 위기: 값싼 판정으로는 갈림길이 하나도 안 보인다. 하지만 BIVALENT였던 상태에서 온 길이므로 판정 오차일 가능성이 높다.
                // 프로브를 늘리고(적응형 프로브) 후보 제한(K)도 풀어 pending 전체를 다시 본다.
                // 이미 갈림길을 잃은 경로에서는 이 비싼 확인을 하지 않는다.
                pick = firstBivalent(byAge, true);
                if (pick != null) {
                    escalations++;
                }
            }
        }
        onBivalentPath = pick != null;
        if (pick == null) {
            pick = oldest;
            failures++;
            failureSteps.add(chosen.size());
        }

        maxWait = Math.max(maxWait, age(pick, step));
        chosen.add(pick.seq());
        return pick;
    }

    /** 보조정리 2: 마감을 넘긴 e를 처리하되, 가능하면 다른 이벤트를 먼저 끼워 넣어 BIVALENT를 유지한다. */
    private Event pickForOverdue(List<Event> byAge, Event overdue, long step) {
        if (staysBivalent(overdue)) {
            return overdue;
        }
        if (age(overdue, step) >= deadline + insertBudget) {
            return null; // 예산 소진. 호출한 쪽이 overdue를 강제로 꺼내고 실패로 기록한다.
        }
        for (Event x : byAge.subList(0, Math.min(maxCandidates, byAge.size()))) {
            if (x == overdue) {
                continue;
            }
            List<Long> afterX = prefixWith(x.seq());
            if (probe.probe(afterX).isBivalent() && probe.probe(withSeq(afterX, overdue.seq())).isBivalent()) {
                inserts++;
                return x;
            }
        }
        return null;
    }

    private Event firstBivalent(List<Event> candidates, boolean deep) {
        for (Event c : candidates) {
            if (staysBivalent(c, deep)) {
                return c;
            }
        }
        return null;
    }

    private boolean staysBivalent(Event e) {
        return staysBivalent(e, false) || staysBivalent(e, true);
    }

    private boolean staysBivalent(Event e, boolean deep) {
        List<Long> next = prefixWith(e.seq());
        return (deep ? probe.probeDeep(next) : probe.probe(next)).isBivalent();
    }

    private List<Long> prefixWith(long seq) {
        return withSeq(chosen, seq);
    }

    private static List<Long> withSeq(List<Long> base, long seq) {
        List<Long> next = new ArrayList<>(base.size() + 1);
        next.addAll(base);
        next.add(seq);
        return next;
    }

    private long age(Event e, long step) {
        return step - firstSeen.get(e.seq());
    }

    /** 꺼낸 이벤트 중 가장 오래 기다린 step 수. */
    public long maxWait() {
        return maxWait;
    }

    /** BIVALENT를 유지하지 못해 가장 오래된 이벤트를 그냥 꺼낸 횟수. */
    public long failures() {
        return failures;
    }

    /** 매 단계 시작 시점에 관찰한 "가장 오래 기다린 pending 이벤트"의 대기 step 최댓값(꺼내지 못한 채 남은 이벤트까지 포함). */
    public long maxObservedAge() {
        return maxObservedAge;
    }

    /** 적대자가 BIVALENT를 유지하지 못한 단계의 번호(0부터, 그 단계 직전까지 꺼낸 이벤트 수). */
    public List<Integer> failureSteps() {
        return List.copyOf(failureSteps);
    }

    /** 값싼 판정으로는 후보가 모두 탈락해, 프로브를 늘려 다시 확인한 끝에 갈림길을 찾은 횟수. */
    public long escalations() {
        return escalations;
    }

    /** 마감을 넘긴 이벤트 앞에 다른 이벤트를 끼워 넣어 BIVALENT를 유지한 횟수(보조정리 2의 적용 횟수). */
    public long inserts() {
        return inserts;
    }

    /** 지금까지 꺼낸 이벤트 seq 목록. 이 접두사로 같은 상태를 언제든 재생할 수 있다. */
    public List<Long> chosenSeqs() {
        return List.copyOf(chosen);
    }

    public Stats stats() {
        return new Stats(chosen.size(), maxWait, failures, inserts, probe.runCount());
    }
}
