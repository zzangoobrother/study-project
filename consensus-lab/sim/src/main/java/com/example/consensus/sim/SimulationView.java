package com.example.consensus.sim;

import java.util.List;

/**
 * 시뮬레이션 상태의 읽기 전용 관찰 창.
 * Scheduler(FLP 적대자), Invariant, 종료 조건, 테스트가 공통으로 사용한다.
 */
public interface SimulationView {

    long now();

    /** 지금까지 처리한 이벤트 수. */
    long steps();

    /** 전체 노드, NodeId 순. */
    List<NodeId> nodes();

    boolean isAlive(NodeId node);

    /** 살아 있는 노드 객체를 반환한다. 크래시 상태이면 IllegalStateException. */
    <T> T node(NodeId node, Class<T> type);

    List<Decision> decisions();

    /** 아직 실행되지 않은 이벤트(읽기 전용). */
    List<Event> pending();
}
