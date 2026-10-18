package com.example.consensus.sim.trace;

import com.example.consensus.sim.DropReason;
import com.example.consensus.sim.NodeId;

/**
 * 실행 중 일어난 사건 한 건의 기록. 모두 record라 두 실행의 trace를 equals로 바로 비교할 수 있다.
 */
public sealed interface TraceEntry {

    long time();

    record Send(long time, NodeId from, NodeId to, Object message) implements TraceEntry {}

    record Deliver(long time, NodeId from, NodeId to, Object message) implements TraceEntry {}

    record Drop(long time, NodeId from, NodeId to, Object message, DropReason reason) implements TraceEntry {}

    record TimerFired(long time, NodeId node, Object key) implements TraceEntry {}

    record TimerDropped(long time, NodeId node, Object key, DropReason reason) implements TraceEntry {}

    record Crash(long time, NodeId node) implements TraceEntry {}

    record Restart(long time, NodeId node) implements TraceEntry {}

    record Decide(long time, NodeId node, Object value) implements TraceEntry {}

    record ActionRun(long time, String name) implements TraceEntry {}
}
