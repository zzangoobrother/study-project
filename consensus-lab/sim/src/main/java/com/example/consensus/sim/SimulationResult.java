package com.example.consensus.sim;

import com.example.consensus.sim.trace.Trace;
import java.util.List;

/**
 * 한 번의 실행 결과 요약. 테스트와 SeedRunner가 이 값만 보고 판단할 수 있게 한다.
 * violation은 reason이 INVARIANT_VIOLATED일 때만 non-null이다.
 * dropped는 유실된 메시지 수(네트워크 유실 + 파티션 + 크래시 노드행)이다.
 */
public record SimulationResult(
        StopReason reason,
        long finalTime,
        long steps,
        long sent,
        long delivered,
        long dropped,
        List<Decision> decisions,
        Violation violation,
        Trace trace) {}
