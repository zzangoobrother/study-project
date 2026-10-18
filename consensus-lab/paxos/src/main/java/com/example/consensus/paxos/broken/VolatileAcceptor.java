package com.example.consensus.paxos.broken;

import com.example.consensus.paxos.Acceptor;
import com.example.consensus.sim.Context;

/**
 * 틀린 변형: promised/accepted 상태를 Storage가 아닌 객체 필드에 둔다.
 *
 * <p>빼먹은 것: 응답 전에 상태를 안정 저장소에 남기는 규칙. 노드가 크래시했다 재시작하면 새 객체가 만들어져
 * 약속과 accept한 값을 모두 잊는다. 과반이 값을 accept한 뒤 그 과반이 한꺼번에 재시작하면, 1단계가
 * "아무도 accept한 적 없다"는 Promise만 받아 자기 값을 새로 제안할 수 있고, 그러면 다른 값이 또 선택된다.
 */
public class VolatileAcceptor extends Acceptor {

    private State state = State.EMPTY;

    @Override
    protected State load(Context ctx) {
        return state;
    }

    @Override
    protected void store(Context ctx, State newState) {
        this.state = newState;
    }
}
