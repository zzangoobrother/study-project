package com.example.consensus.raft;

import com.example.consensus.sim.NodeId;
import com.example.consensus.sim.SimulationControl;
import com.example.consensus.sim.SimulationView;
import java.util.ArrayList;
import java.util.List;

/**
 * 테스트가 "클라이언트"로서 현재 리더에게 명령을 넣는 도구. Action 안에서 SimulationControl로 부른다.
 * 분할 중에는 자기가 리더라고 믿는 노드가 둘 이상일 수 있다(임기가 낮은 옛 리더).
 */
public final class RaftClients {

    private RaftClients() {}

    /** 살아 있고 지금 LEADER 역할인 노드들. NodeId 순. */
    public static List<NodeId> leaders(SimulationView view) {
        List<NodeId> leaders = new ArrayList<>();
        for (NodeId id : view.nodes()) {
            if (view.isAlive(id) && view.node(id, RaftNode.class).role() == Role.LEADER) {
                leaders.add(id);
            }
        }
        return leaders;
    }

    /** 임기가 가장 높은 리더. 없으면 null. 같은 임기에 리더가 둘일 수는 없다(Election Safety). */
    public static NodeId currentLeader(SimulationView view) {
        NodeId best = null;
        long bestTerm = -1;
        for (NodeId id : leaders(view)) {
            long term = view.node(id, RaftNode.class).currentTerm();
            if (term > bestTerm) {
                best = id;
                bestTerm = term;
            }
        }
        return best;
    }

    /** 리더라고 믿는 모든 노드에 명령을 넣는다. 받아 준 노드 수를 돌려준다. */
    public static int submitToLeaders(SimulationControl control, String command) {
        int accepted = 0;
        for (NodeId id : leaders(control.view())) {
            if (control.view().node(id, RaftNode.class).propose(command)) {
                accepted++;
            }
        }
        return accepted;
    }

    /** 특정 노드에 명령을 넣는다. 그 노드가 죽었거나 리더가 아니면 false. */
    public static boolean submitTo(SimulationControl control, NodeId node, String command) {
        return control.view().isAlive(node) && control.view().node(node, RaftNode.class).propose(command);
    }
}
