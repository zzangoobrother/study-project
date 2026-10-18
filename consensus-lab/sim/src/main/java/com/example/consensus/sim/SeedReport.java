package com.example.consensus.sim;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 여러 시드를 돌린 통계 요약. 블로그에 옮기기 좋은 문자열은 toString()이 만든다.
 * firstViolationSeeds는 위반이 난 시드 중 앞쪽 최대 10개다.
 */
public record SeedReport(
        long fromSeed,
        long toSeed,
        int runs,
        Map<StopReason, Integer> reasonCounts,
        int violations,
        List<Long> firstViolationSeeds,
        double avgSteps,
        double avgFinalTime,
        double avgSent,
        double avgDelivered,
        double avgDropped) {

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "시드 %d..%d, 실행 %d회%n", fromSeed, toSeed, runs));
        sb.append("종료 사유:");
        for (StopReason r : StopReason.values()) {
            sb.append(' ').append(r).append('=').append(reasonCounts.getOrDefault(r, 0));
        }
        sb.append('\n');
        sb.append("위반: ").append(violations).append("건");
        if (!firstViolationSeeds.isEmpty()) {
            sb.append(" (첫 위반 시드 ").append(firstViolationSeeds).append(')');
        }
        sb.append('\n');
        sb.append(String.format(Locale.ROOT,
                "평균: steps=%.1f, 최종시각=%.1f, 보냄=%.1f, 배달=%.1f, 유실=%.1f",
                avgSteps, avgFinalTime, avgSent, avgDelivered, avgDropped));
        return sb.toString();
    }
}
