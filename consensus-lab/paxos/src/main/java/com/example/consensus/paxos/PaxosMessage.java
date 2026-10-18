package com.example.consensus.paxos;

/**
 * Paxos 메시지. Context.send는 참조를 그대로 넘기므로 모두 불변 record다.
 * 논문 용어와의 대응: Prepare/Promise = 1a/1b, Accept/Accepted = 2a/2b.
 */
public sealed interface PaxosMessage {

    /** 1a. "ballot보다 낮은 제안은 앞으로 받지 말아 달라." */
    record Prepare(Ballot ballot) implements PaxosMessage {}

    /** 1b. 약속과 함께 지금까지 accept한 (ballot, value)를 알려 준다. 없으면 둘 다 null. */
    record Promise(Ballot ballot, Ballot acceptedBallot, Object acceptedValue) implements PaxosMessage {}

    /** 2a. "ballot으로 value를 accept해 달라." */
    record Accept(Ballot ballot, Object value) implements PaxosMessage {}

    /** 2b. acceptor가 accept했음을 모든 Learner에게 알린다. */
    record Accepted(Ballot ballot, Object value) implements PaxosMessage {}

    /** 논문에는 없는 최적화. 거절하면서 더 높은 약속을 알려 줘 Proposer가 빨리 포기하게 한다. */
    record Nack(Ballot ballot, Ballot promisedBallot) implements PaxosMessage {}
}
