package com.example.consensus.paxos.broken;

import com.example.consensus.paxos.Ballot;
import com.example.consensus.paxos.PaxosConfig;
import com.example.consensus.paxos.Proposer;
import com.example.consensus.sim.Context;

/**
 * 틀린 변형: 1단계(Prepare/Promise)를 건너뛰고 바로 Accept(ballot, 자기 값)를 보낸다.
 *
 * <p>빼먹은 것: 1단계 전체. 1단계는 "낮은 번호는 더 이상 받지 않겠다"는 약속을 받고, 이미 accept된 값이
 * 있는지 알아내는 장치다. 둘 다 없으니 이미 값이 선택된 뒤에도 더 높은 ballot의 Accept가 그대로 받아들여져
 * 선택된 값이 뒤집힌다(agreement 위반). 한 번에 한 Proposer만 있을 때는 우연히 맞게 동작해서 눈에 띄지 않는다.
 */
public class SkipPrepareProposer extends Proposer {

    public SkipPrepareProposer(PaxosConfig config, Object ownValue) {
        super(config, ownValue);
    }

    @Override
    protected void sendPrepare(Context ctx, Ballot ballot) {
        sendAccept(ctx, ballot, ownValue());
    }
}
