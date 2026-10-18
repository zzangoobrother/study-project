package com.example.consensus.sim;

/** {@link Context#decide(Object)} 호출 기록 한 건. */
public record Decision(NodeId node, long time, Object value) {}
