package com.example.consensus.flp;

import com.example.consensus.paxos.PaxosConfig;
import com.example.consensus.paxos.Proposer;
import com.example.consensus.sim.Context;

/**
 * 스스로는 라운드를 시작하지 않는 제안자. 시작 타이머를 걸지 않으므로 "나설 타이머가 없는" 예비 제안자를 만든다.
 * 값은 갖고 있지만 아무도 깨워 주지 않으면 영원히 가만히 있다. FLP 딜레마 실험에서 예비 제안자의 두 모습
 * (타이머 없음 / 타이머 있음) 중 앞의 것을 표현한다. 타이머가 있는 쪽은 일반 {@link Proposer}에 startOffset을 주면 된다.
 */
public final class PassiveProposer extends Proposer {

    public PassiveProposer(PaxosConfig config, Object ownValue) {
        super(config, ownValue);
    }

    @Override
    public void onStart(Context ctx) {
        // 시작 타이머를 걸지 않는다.
    }
}
