package com.example.consensus.sim;

/** {@link Event.Action}이 실행될 때 받는 제어 핸들. 시뮬레이션을 즉시 조작할 수 있다. */
public interface SimulationControl {

    long now();

    /** 노드를 즉시 크래시시킨다. 이미 죽어 있으면 무시한다. */
    void crash(NodeId node);

    /** 노드를 즉시 재시작한다. 이미 살아 있으면 무시한다. */
    void restart(NodeId node);

    SimulationView view();
}
