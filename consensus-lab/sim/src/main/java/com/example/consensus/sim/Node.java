package com.example.consensus.sim;

/**
 * 합의 알고리즘 구현체가 따르는 단일 스레드 액터 계약.
 * 콜백 안에서는 전달받은 {@link Context}로만 바깥 세계(메시지, 타이머, 저장소, 결정)에 영향을 준다.
 */
public interface Node {

    /** 최초 시작 또는 크래시 후 재시작 직후에 한 번 호출된다. */
    void onStart(Context ctx);

    void onMessage(Context ctx, NodeId from, Object message);

    void onTimer(Context ctx, Object timerKey);
}
