package com.example.consensus.sim.trace;

import com.example.consensus.sim.NodeId;
import java.util.Set;
import java.util.TreeSet;

/**
 * 다이어그램이 길어지지 않도록 하는 출력 옵션.
 * nodes가 비어 있으면 전체 노드, 아니면 해당 노드끼리의 사건만 그린다.
 */
public record MermaidOptions(int maxLines, Set<NodeId> nodes) {

    public MermaidOptions {
        nodes = Set.copyOf(new TreeSet<>(nodes));
    }

    public static MermaidOptions defaults() {
        return new MermaidOptions(Integer.MAX_VALUE, Set.of());
    }

    public MermaidOptions withMaxLines(int maxLines) {
        return new MermaidOptions(maxLines, nodes);
    }

    public MermaidOptions onlyNodes(NodeId... ids) {
        return new MermaidOptions(maxLines, Set.of(ids));
    }
}
