package com.example.consensus.sim;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;

/**
 * 시드 범위를 훑으며 시뮬레이션을 반복 실행해 통계를 낸다.
 * "이 시드에서만 깨진다"를 찾고, 위반 시드로 같은 실행을 그대로 재현할 수 있게 하는 측정 도구.
 */
public final class SeedRunner {

    private static final int MAX_VIOLATION_SEEDS = 10;

    private SeedRunner() {}

    /** fromSeed부터 toSeed까지(양끝 포함) factory가 만든 시뮬레이션을 실행한다. */
    public static SeedReport run(long fromSeed, long toSeed, LongFunction<Simulation> factory) {
        Map<StopReason, Integer> counts = new EnumMap<>(StopReason.class);
        List<Long> violationSeeds = new ArrayList<>();
        int runs = 0;
        int violations = 0;
        double steps = 0, time = 0, sent = 0, delivered = 0, dropped = 0;

        for (long seed = fromSeed; seed <= toSeed; seed++) {
            SimulationResult r = factory.apply(seed).run();
            runs++;
            counts.merge(r.reason(), 1, Integer::sum);
            if (r.reason() == StopReason.INVARIANT_VIOLATED) {
                violations++;
                if (violationSeeds.size() < MAX_VIOLATION_SEEDS) {
                    violationSeeds.add(seed);
                }
            }
            steps += r.steps();
            time += r.finalTime();
            sent += r.sent();
            delivered += r.delivered();
            dropped += r.dropped();
        }

        double n = Math.max(runs, 1);
        return new SeedReport(fromSeed, toSeed, runs, counts, violations, List.copyOf(violationSeeds),
                steps / n, time / n, sent / n, delivered / n, dropped / n);
    }
}
