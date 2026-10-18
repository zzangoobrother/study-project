package com.example.consensus.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.Predicate;

/** {@link Simulation} 구성용 빌더. 이벤트의 seq는 build() 시점에 호출 순서대로 부여된다. */
public final class SimulationBuilder {

    long seed = 0L;
    int nodeCount = 0;
    Function<NodeId, Node> factory;
    NetworkPolicy network = new ReliableNetwork(1, 10);
    Scheduler scheduler = new TimeOrderScheduler();
    final List<Invariant> invariants = new ArrayList<>();
    final List<LongFunction<Event>> scheduled = new ArrayList<>();
    Predicate<SimulationView> stopWhen = view -> false;
    long maxSteps = 100_000;

    SimulationBuilder() {}

    public SimulationBuilder seed(long seed) {
        this.seed = seed;
        return this;
    }

    /** N1..Nn 노드를 만든다. factory는 최초 시작과 재시작 때마다 호출된다. */
    public SimulationBuilder nodes(int count, Function<NodeId, Node> factory) {
        this.nodeCount = count;
        this.factory = factory;
        return this;
    }

    public SimulationBuilder network(NetworkPolicy network) {
        this.network = network;
        return this;
    }

    public SimulationBuilder scheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
        return this;
    }

    public SimulationBuilder invariant(Invariant invariant) {
        this.invariants.add(invariant);
        return this;
    }

    public SimulationBuilder crashAt(long time, NodeId node) {
        scheduled.add(seq -> new Event.Crash(time, seq, node));
        return this;
    }

    public SimulationBuilder restartAt(long time, NodeId node) {
        scheduled.add(seq -> new Event.Restart(time, seq, node));
        return this;
    }

    public SimulationBuilder at(long time, String name, Consumer<SimulationControl> body) {
        scheduled.add(seq -> new Event.Action(time, seq, name, body));
        return this;
    }

    public SimulationBuilder stopWhen(Predicate<SimulationView> condition) {
        this.stopWhen = condition;
        return this;
    }

    public SimulationBuilder maxSteps(long maxSteps) {
        this.maxSteps = maxSteps;
        return this;
    }

    public Simulation build() {
        if (factory == null || nodeCount <= 0) {
            throw new IllegalStateException("nodes(count, factory)를 먼저 지정해야 한다");
        }
        return new Simulation(this);
    }
}
