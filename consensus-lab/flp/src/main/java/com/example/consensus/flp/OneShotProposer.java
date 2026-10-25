package com.example.consensus.flp;

import com.example.consensus.paxos.Ballot;
import com.example.consensus.paxos.PaxosConfig;
import com.example.consensus.paxos.Proposer;
import com.example.consensus.sim.Context;

/**
 * 재시도 타이머를 걸지 않는 제안자. 시작 타이머로 라운드를 한 번 시작하고, 그 뒤로는 응답이 오는 대로만 진행한다.
 * <p>
 * 비동기 모델에서는 "타이머가 응답보다 먼저 울리는 순서"도 합법이라, 타이머가 있는 한 적대자는 그것을 이용해
 * 제안자 하나만 있어도 라운드를 계속 끊을 수 있다. 타이머가 전혀 없는 제안자는 그 수단을 잃으므로 공정한 스케줄에서는 반드시 끝난다.
 * 대조군 실험(타이머 없음 + 적대자)에서 쓴다. 실제 시스템에서 쓸 구현은 아니다(메시지가 유실되면 영원히 멈춘다).
 */
public final class OneShotProposer extends Proposer {

    public OneShotProposer(PaxosConfig config, Object ownValue) {
        super(config, ownValue);
    }

    @Override
    protected void scheduleRetry(Context ctx, Ballot ballot) {
        // 재시도 타이머를 걸지 않는다.
    }
}
