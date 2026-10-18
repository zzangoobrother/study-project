package com.example.consensus.sim;

/** Invariant 위반이 일어난 시점(처리한 step 수, 가상 시각)과 메시지. */
public record Violation(long step, long time, String message) {}
