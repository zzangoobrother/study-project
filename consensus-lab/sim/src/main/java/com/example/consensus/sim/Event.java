package com.example.consensus.sim;

import java.util.function.Consumer;

/**
 * 시뮬레이션이 처리하는 모든 사건. 대기열(pending)에 쌓였다가 {@link Scheduler}가 고른 순서로 실행된다.
 * at은 예정 시각, seq는 전역 단조 증가 번호로 같은 시각 이벤트의 순서를 결정적으로 만든다.
 */
public sealed interface Event {

    long at();

    long seq();

    record Delivery(long at, long seq, NodeId from, NodeId to, Object message) implements Event {}

    /** incarnation은 타이머를 건 시점의 노드 세대. 재시작 전에 건 타이머를 걸러내는 데 쓴다. */
    record Timer(long at, long seq, NodeId node, Object key, int incarnation) implements Event {}

    record Crash(long at, long seq, NodeId node) implements Event {}

    record Restart(long at, long seq, NodeId node) implements Event {}

    /** 임의 동작(예: 특정 시각에 파티션 켜기). */
    record Action(long at, long seq, String name, Consumer<SimulationControl> body) implements Event {}
}
