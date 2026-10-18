package com.example.consensus.sim;

import java.util.List;
import java.util.Random;

/** 확률 dropRate로 메시지를 유실하고, 살아남은 메시지에는 [minDelay, maxDelay] 지연을 주는 네트워크. */
public final class LossyNetwork implements NetworkPolicy {

    private final double dropRate;
    private final long minDelay;
    private final long maxDelay;

    public LossyNetwork(double dropRate, long minDelay, long maxDelay) {
        if (dropRate < 0.0 || dropRate > 1.0) {
            throw new IllegalArgumentException("dropRate는 0.0~1.0 이어야 한다: " + dropRate);
        }
        if (minDelay < 0 || maxDelay < minDelay) {
            throw new IllegalArgumentException("지연 범위가 올바르지 않다: " + minDelay + ".." + maxDelay);
        }
        this.dropRate = dropRate;
        this.minDelay = minDelay;
        this.maxDelay = maxDelay;
    }

    @Override
    public List<PlannedDelivery> onSend(NodeId from, NodeId to, Object message, long now, Random random) {
        // 유실 여부를 먼저 뽑고 지연을 뽑는다. 이 순서가 난수 소비 순서를 고정한다.
        if (random.nextDouble() < dropRate) {
            return List.of();
        }
        return List.of(new PlannedDelivery(now + minDelay + random.nextLong(maxDelay - minDelay + 1)));
    }
}
