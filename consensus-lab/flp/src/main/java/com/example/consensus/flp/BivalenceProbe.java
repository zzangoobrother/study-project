package com.example.consensus.flp;

import com.example.consensus.sim.Simulation;
import com.example.consensus.sim.SimulationResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 갈림길 판정기. "시드 + 이벤트 선택 접두사"가 만드는 상태에서 출발해, 무작위 스케줄러로 N번 끝까지(첫 결정까지) 돌려
 * 도달한 결정 값의 집합으로 그 상태의 가치({@link Valence})를 추정한다.
 * <p>
 * 비대칭에 주의한다. 서로 다른 두 값이 실제로 관찰되면 BIVALENT는 확실하다(그 두 실행이 증거다).
 * 하지만 값이 하나만 관찰된 UNIVALENT는 "표본상 하나"일 뿐 확실하지 않다. 프로브 수가 적거나 무작위 스케줄이
 * 편향되어 다른 값에 이르는 길을 못 찾았을 수 있다. 그래서 이 판정기를 쓰는 적대자는 보수적이다.
 * 진짜 BIVALENT를 UNIVALENT로 잘못 보는 일은 있어도, 그 반대는 없다.
 * <p>
 * 접두사 안에서 이미 결정이 나왔다면 그 값이 곧 결과다(agreement가 지켜지면 한 값뿐이므로 UNIVALENT).
 * 같은 접두사는 캐시한다. 캐시는 조회만 하므로 결정성에 영향이 없다.
 */
public final class BivalenceProbe {

    private final SimulationSource source;
    private final int probes;
    private final int probeMaxSteps;
    private final long probeSeed;
    private final Map<List<Long>, ProbeResult> cache = new HashMap<>();
    private long runCount = 0;

    /**
     * @param probes        접두사마다 돌릴 무작위 실행 수의 상한
     * @param probeMaxSteps 접두사 이후 한 번의 프로브가 더 진행할 수 있는 step 상한
     * @param probeSeed     프로브용 스케줄러 난수의 기준 시드(시뮬레이션 시드와는 별개)
     */
    public BivalenceProbe(SimulationSource source, int probes, int probeMaxSteps, long probeSeed) {
        this.source = source;
        this.probes = probes;
        this.probeMaxSteps = probeMaxSteps;
        this.probeSeed = probeSeed;
    }

    /** 지금까지 돌린 프로브 실행(시뮬레이션 재생 포함 1회) 총수. 캐시 적중은 세지 않는다. */
    public long runCount() {
        return runCount;
    }

    public ProbeResult probe(List<Long> prefix) {
        List<Long> key = List.copyOf(prefix);
        ProbeResult cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Set<Object> values = new LinkedHashSet<>();
        int runs = 0;
        for (int i = 0; i < probes && values.size() < 2; i++) {
            runs++;
            runCount++;
            Simulation sim = source.newBuilder()
                    .scheduler(new PrefixReplayScheduler(key, new RandomScheduler(probeSeed * 1_000_003L + i)))
                    .stopWhen(view -> !view.decisions().isEmpty())
                    .maxSteps((long) key.size() + probeMaxSteps)
                    .build();
            SimulationResult result = sim.run();
            if (!result.decisions().isEmpty()) {
                values.add(result.decisions().get(0).value());
            }
        }
        Valence valence = values.size() >= 2 ? Valence.BIVALENT : values.size() == 1 ? Valence.UNIVALENT : Valence.UNKNOWN;
        ProbeResult result = new ProbeResult(valence, new ArrayList<>(values), runs);
        cache.put(key, result);
        return result;
    }
}
