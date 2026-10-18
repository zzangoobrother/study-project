package com.example.consensus.paxos;

import com.example.consensus.sim.Context;
import com.example.consensus.sim.NodeId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 모든 노드가 Acceptor이자 Learner이므로, 역할들이 공유하는 "전체 노드"와 "과반" 계산. */
final class Cluster {

    private Cluster() {}

    /** 자신을 포함한 전체 노드(NodeId 순). Context.peers()는 자신을 뺀다. */
    static List<NodeId> all(Context ctx) {
        List<NodeId> all = new ArrayList<>(ctx.peers());
        all.add(ctx.self());
        Collections.sort(all);
        return all;
    }

    static int quorum(Context ctx) {
        return (ctx.peers().size() + 1) / 2 + 1;
    }

    /** 자기 자신에게도 같은 경로(네트워크)로 보낸다. 자기 Acceptor를 특별 취급하지 않아야 코드가 단순해진다. */
    static void sendToAll(Context ctx, Object message) {
        for (NodeId node : all(ctx)) {
            ctx.send(node, message);
        }
    }
}
