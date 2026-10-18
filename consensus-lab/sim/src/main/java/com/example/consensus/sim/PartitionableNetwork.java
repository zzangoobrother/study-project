package com.example.consensus.sim;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * 다른 NetworkPolicy를 감싸 실행 중에 네트워크 파티션을 켜고 끌 수 있게 하는 데코레이터.
 * 파티션 판단은 보내는 순간에 이뤄진다. 이미 날아가고 있는 메시지는 파티션이 생겨도 배달된다.
 */
public final class PartitionableNetwork implements NetworkPolicy {

    private static final int UNLISTED_GROUP = -1;

    private final NetworkPolicy delegate;
    private final Map<NodeId, Integer> groupOf = new TreeMap<>();

    public PartitionableNetwork(NetworkPolicy delegate) {
        this.delegate = delegate;
    }

    /** 그룹 간 메시지를 유실시킨다. 어느 그룹에도 없는 노드끼리는 서로만 통신할 수 있다. */
    @SafeVarargs
    public final void partition(Set<NodeId>... groups) {
        groupOf.clear();
        for (int i = 0; i < groups.length; i++) {
            for (NodeId id : groups[i]) {
                groupOf.put(id, i);
            }
        }
    }

    public void heal() {
        groupOf.clear();
    }

    private boolean isPartitioned(NodeId from, NodeId to) {
        return !groupOf.getOrDefault(from, UNLISTED_GROUP).equals(groupOf.getOrDefault(to, UNLISTED_GROUP));
    }

    @Override
    public List<PlannedDelivery> onSend(NodeId from, NodeId to, Object message, long now, Random random) {
        if (isPartitioned(from, to)) {
            return List.of();
        }
        return delegate.onSend(from, to, message, now, random);
    }

    @Override
    public DropReason dropReason(NodeId from, NodeId to) {
        return isPartitioned(from, to) ? DropReason.PARTITION : delegate.dropReason(from, to);
    }
}
