package com.example.consensus.sim;

/**
 * 매 이벤트 처리 후 호출되는 안전성 검사. 위반이면 {@link InvariantViolation}을 던진다.
 */
@FunctionalInterface
public interface Invariant {

    void check(SimulationView view);
}
