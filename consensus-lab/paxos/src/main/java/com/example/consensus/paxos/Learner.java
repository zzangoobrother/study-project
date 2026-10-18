package com.example.consensus.paxos;

import com.example.consensus.paxos.PaxosMessage.Accepted;
import com.example.consensus.sim.Context;
import com.example.consensus.sim.NodeId;
import com.example.consensus.sim.Storage;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 논문 2.3절의 Learner. 값이 "선택"되었다는 것은 같은 ballot으로 과반 Acceptor가 accept했다는 뜻이다.
 */
public class Learner {

    private static final String DECIDED_KEY = "learner.decided";

    /** ballot별로 Accepted를 보낸 acceptor 집합. 다른 ballot의 표를 합치면 안 된다. */
    private final Map<Ballot, Set<NodeId>> votes = new TreeMap<>();

    public void onAccepted(Context ctx, NodeId from, Accepted msg) {
        if (isDecided(ctx.storage())) {
            return;
        }
        // 집합이라 같은 acceptor의 중복 메시지는 한 표로 센다.
        Set<NodeId> voters = votes.computeIfAbsent(msg.ballot(), b -> new TreeSet<>());
        voters.add(from);
        if (voters.size() >= Cluster.quorum(ctx)) {
            // 결정 여부를 Storage에 남겨, 재시작 후에도 노드당 한 번만 decide 한다.
            ctx.storage().put(DECIDED_KEY, new Decided(msg.value()));
            ctx.decide(msg.value());
        }
    }

    /** value가 null이어도 구분할 수 있도록 감싼다. */
    private record Decided(Object value) {}

    public static boolean isDecided(Storage storage) {
        return storage.get(DECIDED_KEY) != null;
    }
}
