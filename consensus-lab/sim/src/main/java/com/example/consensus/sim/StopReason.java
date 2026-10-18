package com.example.consensus.sim;

/** 시뮬레이션이 끝난 이유. */
public enum StopReason {
    STOP_CONDITION,
    MAX_STEPS,
    NO_MORE_EVENTS,
    INVARIANT_VIOLATED
}
