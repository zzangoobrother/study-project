package com.example.consensus.sim;

import java.util.List;
import java.util.Random;

/**
 * "보내는 순간"의 결정: 이 메시지를 유실할지, 중복할지, 언제 도착시킬지.
 * 빈 리스트는 유실, 원소 2개는 중복 배달이다. 난수는 반드시 인자로 받은 Random만 사용한다.
 */
public interface NetworkPolicy {

    /** 배달 예정 한 건. at은 절대 가상 시각이며 now 이상이어야 한다. */
    record PlannedDelivery(long at) {}

    List<PlannedDelivery> onSend(NodeId from, NodeId to, Object message, long now, Random random);

    /** onSend가 빈 리스트를 돌려줬을 때 trace에 남길 유실 사유. */
    default DropReason dropReason(NodeId from, NodeId to) {
        return DropReason.NETWORK;
    }
}
