package com.example.consensus.paxos;

import com.example.consensus.paxos.PaxosMessage.Accept;
import com.example.consensus.paxos.PaxosMessage.Accepted;
import com.example.consensus.paxos.PaxosMessage.Nack;
import com.example.consensus.paxos.PaxosMessage.Prepare;
import com.example.consensus.paxos.PaxosMessage.Promise;
import com.example.consensus.sim.Context;
import com.example.consensus.sim.NodeId;

/**
 * 논문의 Acceptor. 약속(promise)과 수락(accept)을 기억하는 것이 Paxos 안전성의 전부다.
 */
public class Acceptor {

    /** 세 값은 항상 함께 바뀌므로 하나의 불변 값으로 묶어 한 번에 저장한다. */
    public record State(Ballot promised, Ballot acceptedBallot, Object acceptedValue) {
        public static final State EMPTY = new State(null, null, null);
    }

    private static final String STATE_KEY = "acceptor.state";

    /** 논문 2.2절 P1b: Prepare(n)에 n이 지금까지 응답한 어떤 Prepare보다 크면 Promise. */
    public void onPrepare(Context ctx, NodeId from, Prepare msg) {
        State s = load(ctx);
        if (msg.ballot().isHigherThan(s.promised())) {
            // 응답을 보내기 전에 저장한다. 먼저 보내고 저장 전에 죽으면, 재시작 후 약속을 잊은 채
            // 이미 약속한 것과 모순되는 Accept를 받아들여 안전성이 깨진다.
            store(ctx, new State(msg.ballot(), s.acceptedBallot(), s.acceptedValue()));
            ctx.send(from, new Promise(msg.ballot(), s.acceptedBallot(), s.acceptedValue()));
        } else {
            ctx.send(from, new Nack(msg.ballot(), s.promised()));
        }
    }

    /** 논문 2.2절 P2b: Promise한 번호보다 작은 번호만 아니면(>=) accept한다. */
    public void onAccept(Context ctx, NodeId from, Accept msg) {
        State s = load(ctx);
        if (msg.ballot().isAtLeast(s.promised())) {
            // 마찬가지로 저장이 먼저, Accepted 통지는 그다음이다.
            store(ctx, new State(msg.ballot(), msg.ballot(), msg.value()));
            // 결정을 알아야 하는 쪽은 Proposer가 아니라 Learner이므로, 모든 노드에 알린다.
            Cluster.sendToAll(ctx, new Accepted(msg.ballot(), msg.value()));
        } else {
            ctx.send(from, new Nack(msg.ballot(), s.promised()));
        }
    }

    /** 상태 읽기. 재시작한 노드는 새 객체지만 Storage에서 그대로 복원한다. */
    protected State load(Context ctx) {
        return ctx.storage().getOrDefault(STATE_KEY, State.EMPTY);
    }

    protected void store(Context ctx, State state) {
        ctx.storage().put(STATE_KEY, state);
    }
}
