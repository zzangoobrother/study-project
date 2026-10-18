package com.example.consensus.paxos.broken;

import com.example.consensus.paxos.PaxosConfig;
import com.example.consensus.paxos.PaxosMessage.Promise;
import com.example.consensus.paxos.Proposer;
import java.util.List;

/**
 * 틀린 변형: 2단계에서 Promise에 담긴 이미 accept된 값을 무시하고 항상 자기 값을 제안한다.
 *
 * <p>빼먹은 것: 논문 2.2절 (2) a의 "Promise 중 번호가 가장 높은 accept 값을 이어받는다" 규칙.
 * 깨지는 상황: 한 Proposer의 값이 과반에 accept되어 이미 선택된 뒤에, 늦게 온 다른 Proposer가
 * 1단계에서 그 값을 보고도 자기 값으로 더 높은 ballot의 Accept를 보낸다. 과반이 새 값으로 다시 accept하면
 * 서로 다른 값이 모두 선택되어 agreement가 깨진다.
 */
public class IgnoreAcceptedValueProposer extends Proposer {

    public IgnoreAcceptedValueProposer(PaxosConfig config, Object ownValue) {
        super(config, ownValue);
    }

    @Override
    protected Object chooseValue(List<Promise> promises) {
        return ownValue();
    }
}
