package com.example.consensus.paxos;

import com.example.consensus.paxos.PaxosMessage.Accept;
import com.example.consensus.paxos.PaxosMessage.Nack;
import com.example.consensus.paxos.PaxosMessage.Prepare;
import com.example.consensus.paxos.PaxosMessage.Promise;
import com.example.consensus.sim.Context;
import com.example.consensus.sim.NodeId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 논문의 Proposer. 라운드 하나는 "1단계(Prepare/Promise) -> 2단계(Accept)"이고,
 * 실패하면 더 높은 round로 새 라운드를 시작한다.
 */
public class Proposer {

    private static final String ROUND_KEY = "proposer.round";

    private enum Phase { IDLE, PREPARING, ACCEPTING, BACKING_OFF }

    // 타이머 키. ballot을 담아 두면 "지금 라운드의 타이머인지" 비교해 오래된 타이머를 버릴 수 있다.
    record StartTimer() {}

    record RetryTimer(Ballot ballot) {}

    record BackoffTimer(Ballot ballot) {}

    private final PaxosConfig config;
    private final Object ownValue;

    private Ballot current;
    private Phase phase = Phase.IDLE;
    private final Map<NodeId, Promise> promises = new TreeMap<>();

    public Proposer(PaxosConfig config, Object ownValue) {
        this.config = config;
        this.ownValue = ownValue;
    }

    protected Object ownValue() {
        return ownValue;
    }

    public void onStart(Context ctx) {
        long delay = config.startDelayMin();
        if (config.startDelayMax() > config.startDelayMin()) {
            delay += ctx.random().nextLong(config.startDelayMax() - config.startDelayMin() + 1);
        }
        delay += config.startOffsets().getOrDefault(ctx.self(), 0L);
        ctx.setTimer(delay, new StartTimer());
    }

    public void onTimer(Context ctx, Object key) {
        switch (key) {
            case StartTimer t -> startRound(ctx);
            // 라운드가 이미 넘어갔다면(current != 타이머의 ballot) 오래된 타이머이므로 무시한다.
            // 백오프 중에는 BackoffTimer가 재시작을 맡는다. 여기서도 재시작하면 백오프가 무력해진다.
            case RetryTimer t when t.ballot().equals(current) && phase != Phase.BACKING_OFF -> startRound(ctx);
            case BackoffTimer t when t.ballot().equals(current) -> startRound(ctx);
            default -> { }
        }
    }

    /** 1단계 시작. 논문 2.2절 (1) a: 번호 n을 고르고 과반 Acceptor에게 Prepare(n)을 보낸다. */
    private void startRound(Context ctx) {
        if (Learner.isDecided(ctx.storage())) {
            phase = Phase.IDLE;
            return;
        }
        // 지금까지 본 가장 높은 round + 1. 저장해 두면 재시작 후에도 같은 ballot을 다시 쓰지 않는다
        // (같은 ballot으로 이미 다른 값이 accept되었을 수 있는데 같은 번호를 재사용하면 안 된다).
        int round = ctx.storage().getOrDefault(ROUND_KEY, 0) + 1;
        ctx.storage().put(ROUND_KEY, round);

        current = new Ballot(round, ctx.self());
        phase = Phase.PREPARING;
        promises.clear();
        sendPrepare(ctx, current);
        // 타이머 자체를 흔든다. 두 proposer가 같은 박자로 재시작하면 서로의 라운드를 매번 같은 순서로 무효화하는
        // 대칭이 깨지지 않는다. 지연에 무작위를 섞어야 한쪽이 먼저 Accept까지 끝낼 틈이 생긴다.
        // jitter가 0이면 난수를 뽑지 않아 기존 실행(난수 스트림)이 바뀌지 않는다.
        long timeout = config.retryTimeout();
        if (config.retryJitter() > 0) {
            timeout += ctx.random().nextLong(config.retryJitter() + 1);
        }
        ctx.setTimer(timeout, new RetryTimer(current));
    }

    protected void sendPrepare(Context ctx, Ballot ballot) {
        Cluster.sendToAll(ctx, new Prepare(ballot));
    }

    /** 논문 2.2절 (2) a: 과반 Promise를 받으면 Accept(n, v)를 보낸다. */
    public void onPromise(Context ctx, NodeId from, Promise msg) {
        if (phase != Phase.PREPARING || !msg.ballot().equals(current)) {
            return;
        }
        promises.put(from, msg);
        if (promises.size() >= Cluster.quorum(ctx)) {
            phase = Phase.ACCEPTING;
            sendAccept(ctx, current, chooseValue(new ArrayList<>(promises.values())));
        }
    }

    /**
     * 안전성의 핵심. Promise 중 acceptedBallot이 가장 높은 값을 이어받는다.
     * 이미 선택되었을 수도 있는 값을 뒤집지 않기 위해서다. 하나도 없을 때만 자기 값을 제안할 자유가 있다.
     */
    protected Object chooseValue(List<Promise> promises) {
        Promise best = null;
        for (Promise p : promises) {
            if (p.acceptedBallot() != null && (best == null || p.acceptedBallot().isHigherThan(best.acceptedBallot()))) {
                best = p;
            }
        }
        return best != null ? best.acceptedValue() : ownValue;
    }

    protected void sendAccept(Context ctx, Ballot ballot, Object value) {
        Cluster.sendToAll(ctx, new Accept(ballot, value));
    }

    public void onNack(Context ctx, Nack msg) {
        // 더 높은 round가 있다는 사실은 라운드가 오래되었어도 유용하다. 다음 round 계산에 반영한다.
        int seen = msg.promisedBallot().round();
        if (seen > ctx.storage().getOrDefault(ROUND_KEY, 0)) {
            ctx.storage().put(ROUND_KEY, seen);
        }
        // 현재 라운드가 거절당했을 때만 포기한다. 같은 라운드의 Nack이 여러 개 와도 한 번만 반응한다.
        if (!msg.ballot().equals(current) || phase == Phase.BACKING_OFF || phase == Phase.IDLE) {
            return;
        }
        long backoff = config.backoffMin();
        if (config.backoffMax() > config.backoffMin()) {
            backoff += ctx.random().nextLong(config.backoffMax() - config.backoffMin() + 1);
        }
        if (backoff == 0) {
            startRound(ctx);
        } else {
            phase = Phase.BACKING_OFF;
            ctx.setTimer(backoff, new BackoffTimer(current));
        }
    }
}
