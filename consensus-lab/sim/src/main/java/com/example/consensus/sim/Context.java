package com.example.consensus.sim;

import java.util.List;
import java.util.Random;

/**
 * 노드가 시뮬레이터와 대화하는 유일한 창구.
 * 스레드·소켓·시스템 시계를 직접 쓰지 못하게 막아, 모든 실행이 시드 하나로 재현되도록 한다.
 */
public interface Context {

    NodeId self();

    /** 자신을 제외한 모든 노드. NodeId 순으로 정렬되어 있다. */
    List<NodeId> peers();

    /**
     * 발신자(from)는 시뮬레이터가 self로 찍으므로 노드가 위조할 수 없다.
     * 메시지는 복사 없이 참조 그대로 전달되므로 불변이어야 한다(record + List.copyOf).
     * 보낸 뒤 원본을 고치면 이미 날아가는 중인 메시지까지 바뀌어, 실제 네트워크에선 없는 버그가 생긴다.
     */
    void send(NodeId to, Object message);

    /** peers 전원에게 send 한다. */
    void broadcast(Object message);

    void setTimer(long delay, Object key);

    /** 가상 시간. */
    long now();

    /** 마스터 시드에서 파생된 노드 전용 난수. */
    Random random();

    /** 크래시 후에도 유지되는 노드별 저장소. */
    Storage storage();

    /** 결정을 기록한다. 한 노드에서 여러 번 호출될 수 있다. */
    void decide(Object value);
}
