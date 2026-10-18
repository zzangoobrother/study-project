package com.example.consensus.paxos;

import com.example.consensus.sim.NodeId;

/**
 * 제안 번호(논문의 proposal number). 클러스터 전체에서 유일하고 전순서가 있어야 한다.
 *
 * <p>round만 쓰면 두 Proposer가 같은 번호를 동시에 쓸 수 있다. 같은 번호에 서로 다른 값이
 * Accept되면 "번호 하나에는 값 하나"라는 가정이 깨지므로, round가 같으면 proposer id로 순서를 정해
 * 서로 다른 Proposer의 ballot이 절대 같아지지 않게 한다.
 */
public record Ballot(int round, NodeId proposer) implements Comparable<Ballot> {

    @Override
    public int compareTo(Ballot other) {
        int byRound = Integer.compare(round, other.round);
        return byRound != 0 ? byRound : proposer.compareTo(other.proposer);
    }

    public boolean isHigherThan(Ballot other) {
        return other == null || compareTo(other) > 0;
    }

    /** other가 null(아무것도 약속 안 함)이면 항상 true. */
    public boolean isAtLeast(Ballot other) {
        return other == null || compareTo(other) >= 0;
    }

    @Override
    public String toString() {
        return round + "." + proposer;
    }
}
