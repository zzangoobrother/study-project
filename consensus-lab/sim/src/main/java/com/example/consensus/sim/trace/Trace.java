package com.example.consensus.sim.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 시뮬레이션이 남기는 이벤트 기록. 결정성 검증과 Mermaid 다이어그램의 원본이다. */
public final class Trace {

    private final List<TraceEntry> entries = new ArrayList<>();

    public void add(TraceEntry entry) {
        entries.add(entry);
    }

    public List<TraceEntry> entries() {
        return Collections.unmodifiableList(entries);
    }
}
