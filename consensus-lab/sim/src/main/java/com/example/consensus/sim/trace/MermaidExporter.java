package com.example.consensus.sim.trace;

import com.example.consensus.sim.NodeId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Trace를 블로그에 붙일 Mermaid sequenceDiagram 텍스트로 바꾼다.
 * 메시지 라벨은 message.toString()이므로 알고리즘 메시지는 record로 만들면 읽기 좋다.
 * send/timer 기록은 그리지 않는다(deliver와 drop이 같은 정보를 담고 있다).
 */
public final class MermaidExporter {

    private MermaidExporter() {}

    private record Line(String text, List<NodeId> nodes) {}

    public static String toSequenceDiagram(Trace trace, MermaidOptions options) {
        List<Line> lines = new ArrayList<>();
        for (TraceEntry e : trace.entries()) {
            Line line = toLine(e, options.nodes());
            if (line != null) {
                lines.add(line);
            }
        }

        int omitted = Math.max(0, lines.size() - options.maxLines());
        List<Line> shown = lines.subList(0, lines.size() - omitted);

        Set<NodeId> participants = new TreeSet<>();
        shown.forEach(l -> participants.addAll(l.nodes()));

        StringBuilder sb = new StringBuilder("sequenceDiagram\n");
        participants.forEach(p -> sb.append("    participant ").append(p).append('\n'));
        shown.forEach(l -> sb.append("    ").append(l.text()).append('\n'));
        if (omitted > 0) {
            sb.append("    %% ... ").append(omitted).append("줄 생략\n");
        }
        return sb.toString();
    }

    private static Line toLine(TraceEntry e, Set<NodeId> filter) {
        return switch (e) {
            case TraceEntry.Deliver d when allowed(filter, d.from(), d.to()) ->
                    new Line(d.from() + "->>" + d.to() + ": " + label(d.message()), List.of(d.from(), d.to()));
            case TraceEntry.Drop d when allowed(filter, d.from(), d.to()) ->
                    new Line(d.from() + "-x" + d.to() + ": " + label(d.message()) + " (dropped)", List.of(d.from(), d.to()));
            case TraceEntry.Crash c when allowed(filter, c.node()) ->
                    new Line("Note over " + c.node() + ": crash", List.of(c.node()));
            case TraceEntry.Restart r when allowed(filter, r.node()) ->
                    new Line("Note over " + r.node() + ": restart", List.of(r.node()));
            case TraceEntry.Decide d when allowed(filter, d.node()) ->
                    new Line("Note over " + d.node() + ": decide " + label(d.value()), List.of(d.node()));
            case TraceEntry.ActionRun a when filter.isEmpty() ->
                    new Line("%% action: " + a.name(), List.of());
            default -> null;
        };
    }

    private static boolean allowed(Set<NodeId> filter, NodeId... ids) {
        if (filter.isEmpty()) {
            return true;
        }
        for (NodeId id : ids) {
            if (!filter.contains(id)) {
                return false;
            }
        }
        return true;
    }

    /** Mermaid 문법을 깨는 세미콜론과 개행을 치환한다. */
    private static String label(Object o) {
        return String.valueOf(o).replace(';', ',').replace('\n', ' ');
    }
}
