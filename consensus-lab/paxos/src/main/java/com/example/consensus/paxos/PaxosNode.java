package com.example.consensus.paxos;

import com.example.consensus.paxos.PaxosMessage.Accept;
import com.example.consensus.paxos.PaxosMessage.Accepted;
import com.example.consensus.paxos.PaxosMessage.Nack;
import com.example.consensus.paxos.PaxosMessage.Prepare;
import com.example.consensus.paxos.PaxosMessage.Promise;
import com.example.consensus.sim.Context;
import com.example.consensus.sim.Node;
import com.example.consensus.sim.NodeId;
import java.util.function.Function;

/**
 * 한 노드 = Acceptor + Learner (+ 설정상 proposer이면 Proposer). 메시지를 역할별로 나눠 줄 뿐이다.
 */
public final class PaxosNode implements Node {

    @FunctionalInterface
    public interface ProposerFactory {
        Proposer create(PaxosConfig config, Object ownValue);
    }

    @FunctionalInterface
    public interface AcceptorFactory {
        Acceptor create();
    }

    private final Acceptor acceptor;
    private final Learner learner = new Learner();
    private final Proposer proposer; // proposer가 아닌 노드는 null

    private PaxosNode(Acceptor acceptor, Proposer proposer) {
        this.acceptor = acceptor;
        this.proposer = proposer;
    }

    /** 변형(broken) 클래스를 끼울 수 있도록 역할 팩토리를 받는 빌더. 기본값은 정상 구현이다. */
    public static Builder builder(PaxosConfig config) {
        return new Builder(config);
    }

    public static final class Builder {
        private final PaxosConfig config;
        private ProposerFactory proposerFactory = Proposer::new;
        private AcceptorFactory acceptorFactory = Acceptor::new;

        private Builder(PaxosConfig config) {
            this.config = config;
        }

        public Builder proposer(ProposerFactory factory) {
            this.proposerFactory = factory;
            return this;
        }

        public Builder acceptor(AcceptorFactory factory) {
            this.acceptorFactory = factory;
            return this;
        }

        /** Simulation.builder().nodes(n, ...)에 그대로 넘기는 팩토리. 재시작 때마다 새 노드 객체를 만든다. */
        public Function<NodeId, Node> factory() {
            return id -> new PaxosNode(
                    acceptorFactory.create(),
                    config.proposals().containsKey(id) ? proposerFactory.create(config, config.proposals().get(id)) : null);
        }
    }

    @Override
    public void onStart(Context ctx) {
        if (proposer != null) {
            proposer.onStart(ctx);
        }
    }

    @Override
    public void onMessage(Context ctx, NodeId from, Object message) {
        switch ((PaxosMessage) message) {
            case Prepare m -> acceptor.onPrepare(ctx, from, m);
            case Accept m -> acceptor.onAccept(ctx, from, m);
            case Accepted m -> learner.onAccepted(ctx, from, m);
            case Promise m -> {
                if (proposer != null) {
                    proposer.onPromise(ctx, from, m);
                }
            }
            case Nack m -> {
                if (proposer != null) {
                    proposer.onNack(ctx, m);
                }
            }
        }
    }

    @Override
    public void onTimer(Context ctx, Object timerKey) {
        if (proposer != null) {
            proposer.onTimer(ctx, timerKey);
        }
    }
}
