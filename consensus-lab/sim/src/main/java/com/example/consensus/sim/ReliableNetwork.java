package com.example.consensus.sim;

import java.util.List;
import java.util.Random;

/** 유실 없이 [minDelay, maxDelay] 범위의 지연만 주는 네트워크. 지연 차이로 인한 순서 뒤바뀜은 일어난다. */
public final class ReliableNetwork implements NetworkPolicy {

    private final long minDelay;
    private final long maxDelay;

    public ReliableNetwork(long minDelay, long maxDelay) {
        if (minDelay < 0 || maxDelay < minDelay) {
            throw new IllegalArgumentException("지연 범위가 올바르지 않다: " + minDelay + ".." + maxDelay);
        }
        this.minDelay = minDelay;
        this.maxDelay = maxDelay;
    }

    @Override
    public List<PlannedDelivery> onSend(NodeId from, NodeId to, Object message, long now, Random random) {
        return List.of(new PlannedDelivery(now + minDelay + random.nextLong(maxDelay - minDelay + 1)));
    }
}
