package com.example.consensus.sim;

import com.example.consensus.sim.trace.Trace;
import com.example.consensus.sim.trace.TraceEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 스레드와 소켓 없이 분산 시스템을 이벤트 루프 하나로 돌리는 결정적 시뮬레이터.
 * 같은 시드와 같은 구성이면 trace가 비트 단위로 같아야 하므로 다음 규칙을 지킨다.
 * <ul>
 *   <li>난수는 시드 하나로 만든 마스터 Random에서만 파생한다(노드별 Random도 여기서 뽑는다).</li>
 *   <li>HashMap/HashSet 순회에 의존하지 않고 TreeMap/ArrayList만 쓴다.</li>
 *   <li>System.currentTimeMillis/nanoTime, 스레드, 실제 I/O를 쓰지 않는다.</li>
 *   <li>같은 시각의 이벤트는 seq로 순서를 정한다.</li>
 * </ul>
 * 한 인스턴스는 한 번만 run() 할 수 있다.
 */
public final class Simulation {

    private static final class Slot {
        final NodeId id;
        final MapStorage storage = new MapStorage();
        final Random random;
        Node node;
        int incarnation = 0;
        boolean alive = false;

        Slot(NodeId id, Random random) {
            this.id = id;
            this.random = random;
        }
    }

    private final Random random;
    private final Function<NodeId, Node> factory;
    private final NetworkPolicy network;
    private final Scheduler scheduler;
    private final List<Invariant> invariants;
    private final Predicate<SimulationView> stopWhen;
    private final long maxSteps;

    private final Map<NodeId, Slot> slots = new TreeMap<>();
    private final List<NodeId> nodeIds;
    private final List<Event> pending = new ArrayList<>();
    private final List<Event> pendingView = Collections.unmodifiableList(pending);
    private final List<Decision> decisions = new ArrayList<>();
    private final List<Decision> decisionsView = Collections.unmodifiableList(decisions);
    private final Trace trace = new Trace();

    private long now = 0;
    private long steps = 0;
    private long nextSeq = 0;
    private long sent = 0;
    private long delivered = 0;
    private long dropped = 0;
    private boolean ran = false;

    private final SimulationView view = new View();
    private final SimulationControl control = new Control();

    public static SimulationBuilder builder() {
        return new SimulationBuilder();
    }

    Simulation(SimulationBuilder b) {
        this.random = new Random(b.seed);
        this.factory = b.factory;
        this.network = b.network;
        this.scheduler = b.scheduler;
        this.invariants = List.copyOf(b.invariants);
        this.stopWhen = b.stopWhen;
        this.maxSteps = b.maxSteps;

        List<NodeId> ids = new ArrayList<>();
        for (int i = 1; i <= b.nodeCount; i++) {
            NodeId id = new NodeId(i);
            ids.add(id);
            slots.put(id, new Slot(id, new Random(random.nextLong())));
        }
        this.nodeIds = Collections.unmodifiableList(ids);

        for (var make : b.scheduled) {
            Event e = make.apply(nextSeq++);
            if (e.at() < 0) {
                throw new IllegalArgumentException("이벤트 시각은 0 이상이어야 한다: " + e);
            }
            pending.add(e);
        }
    }

    public SimulationResult run() {
        if (ran) {
            throw new IllegalStateException("Simulation은 한 번만 실행할 수 있다");
        }
        ran = true;

        for (Slot slot : slots.values()) {
            startNode(slot);
        }

        StopReason reason;
        Violation violation = null;
        while (true) {
            try {
                for (Invariant invariant : invariants) {
                    invariant.check(view);
                }
            } catch (InvariantViolation e) {
                violation = new Violation(steps, now, e.getMessage());
                reason = StopReason.INVARIANT_VIOLATED;
                break;
            }
            if (stopWhen.test(view)) {
                reason = StopReason.STOP_CONDITION;
                break;
            }
            if (steps >= maxSteps) {
                reason = StopReason.MAX_STEPS;
                break;
            }
            if (pending.isEmpty()) {
                reason = StopReason.NO_MORE_EVENTS;
                break;
            }
            step();
        }

        return new SimulationResult(reason, now, steps, sent, delivered, dropped,
                List.copyOf(decisions), violation, trace);
    }

    private void step() {
        Event next = scheduler.pickNext(pendingView, view);
        int index = -1;
        for (int i = 0; i < pending.size(); i++) {
            if (pending.get(i) == next) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new IllegalStateException("Scheduler가 pending에 없는 이벤트를 골랐다: " + next);
        }
        pending.remove(index);
        now = Math.max(now, next.at());
        steps++;

        switch (next) {
            case Event.Delivery d -> deliver(d);
            case Event.Timer t -> fireTimer(t);
            case Event.Crash c -> crash(slots.get(c.node()));
            case Event.Restart r -> restart(slots.get(r.node()));
            case Event.Action a -> {
                trace.add(new TraceEntry.ActionRun(now, a.name()));
                a.body().accept(control);
            }
        }
    }

    private void deliver(Event.Delivery d) {
        Slot target = slots.get(d.to());
        if (!target.alive) {
            dropped++;
            trace.add(new TraceEntry.Drop(now, d.from(), d.to(), d.message(), DropReason.CRASHED));
            return;
        }
        delivered++;
        trace.add(new TraceEntry.Deliver(now, d.from(), d.to(), d.message()));
        target.node.onMessage(new NodeContext(target), d.from(), d.message());
    }

    private void fireTimer(Event.Timer t) {
        Slot slot = slots.get(t.node());
        // 크래시 중이거나, 재시작 전 세대(incarnation)에서 건 타이머는 버린다.
        if (!slot.alive || slot.incarnation != t.incarnation()) {
            trace.add(new TraceEntry.TimerDropped(now, t.node(), t.key(), DropReason.CRASHED));
            return;
        }
        trace.add(new TraceEntry.TimerFired(now, t.node(), t.key()));
        slot.node.onTimer(new NodeContext(slot), t.key());
    }

    private void startNode(Slot slot) {
        slot.node = factory.apply(slot.id);
        slot.alive = true;
        slot.node.onStart(new NodeContext(slot));
    }

    private void crash(Slot slot) {
        if (!slot.alive) {
            return;
        }
        slot.alive = false;
        slot.node = null;
        trace.add(new TraceEntry.Crash(now, slot.id));
    }

    private void restart(Slot slot) {
        if (slot.alive) {
            return;
        }
        slot.incarnation++;
        trace.add(new TraceEntry.Restart(now, slot.id));
        startNode(slot);
    }

    private void enqueue(Event e) {
        pending.add(e);
    }

    private Slot slotOf(NodeId id) {
        Slot slot = slots.get(id);
        if (slot == null) {
            throw new IllegalArgumentException("존재하지 않는 노드: " + id);
        }
        return slot;
    }

    /** 노드 한 세대(incarnation)에 묶인 Context. from은 항상 이 슬롯의 id로 찍힌다. */
    private final class NodeContext implements Context {
        private final Slot slot;
        private final int incarnation;
        private final List<NodeId> peers;

        NodeContext(Slot slot) {
            this.slot = slot;
            this.incarnation = slot.incarnation;
            List<NodeId> others = new ArrayList<>(nodeIds);
            others.remove(slot.id);
            this.peers = Collections.unmodifiableList(others);
        }

        @Override
        public NodeId self() {
            return slot.id;
        }

        @Override
        public List<NodeId> peers() {
            return peers;
        }

        @Override
        public void send(NodeId to, Object message) {
            slotOf(to);
            sent++;
            trace.add(new TraceEntry.Send(now, slot.id, to, message));
            List<NetworkPolicy.PlannedDelivery> plans = network.onSend(slot.id, to, message, now, random);
            if (plans.isEmpty()) {
                dropped++;
                trace.add(new TraceEntry.Drop(now, slot.id, to, message, network.dropReason(slot.id, to)));
                return;
            }
            for (NetworkPolicy.PlannedDelivery plan : plans) {
                if (plan.at() < now) {
                    throw new IllegalStateException("NetworkPolicy가 과거 시각으로 배달을 예약했다: " + plan.at());
                }
                enqueue(new Event.Delivery(plan.at(), nextSeq++, slot.id, to, message));
            }
        }

        @Override
        public void broadcast(Object message) {
            for (NodeId peer : peers) {
                send(peer, message);
            }
        }

        @Override
        public void setTimer(long delay, Object key) {
            if (delay < 0) {
                throw new IllegalArgumentException("delay는 0 이상이어야 한다: " + delay);
            }
            enqueue(new Event.Timer(now + delay, nextSeq++, slot.id, key, incarnation));
        }

        @Override
        public long now() {
            return now;
        }

        @Override
        public Random random() {
            return slot.random;
        }

        @Override
        public Storage storage() {
            return slot.storage;
        }

        @Override
        public void decide(Object value) {
            decisions.add(new Decision(slot.id, now, value));
            trace.add(new TraceEntry.Decide(now, slot.id, value));
        }
    }

    private final class View implements SimulationView {
        @Override
        public long now() {
            return now;
        }

        @Override
        public long steps() {
            return steps;
        }

        @Override
        public List<NodeId> nodes() {
            return nodeIds;
        }

        @Override
        public boolean isAlive(NodeId node) {
            return slotOf(node).alive;
        }

        @Override
        public <T> T node(NodeId id, Class<T> type) {
            Slot slot = slotOf(id);
            if (!slot.alive) {
                throw new IllegalStateException(id + "는 크래시 상태라 노드 객체가 없다");
            }
            return type.cast(slot.node);
        }

        @Override
        public List<Decision> decisions() {
            return decisionsView;
        }

        @Override
        public List<Event> pending() {
            return pendingView;
        }
    }

    private final class Control implements SimulationControl {
        @Override
        public long now() {
            return now;
        }

        @Override
        public void crash(NodeId node) {
            Simulation.this.crash(slotOf(node));
        }

        @Override
        public void restart(NodeId node) {
            Simulation.this.restart(slotOf(node));
        }

        @Override
        public SimulationView view() {
            return view;
        }
    }
}
