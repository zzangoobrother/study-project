package com.example.consensus.sim;

/**
 * 시뮬레이션 안에서 노드를 구분하는 식별자.
 * TreeMap 키와 peers 정렬에 쓰이므로 Comparable이며, trace 가독성을 위해 "N1"처럼 짧게 출력한다.
 */
public record NodeId(int value) implements Comparable<NodeId> {

    @Override
    public int compareTo(NodeId other) {
        return Integer.compare(value, other.value);
    }

    @Override
    public String toString() {
        return "N" + value;
    }
}
