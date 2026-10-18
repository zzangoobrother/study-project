package com.example.consensus.sim;

/**
 * 노드별 영속 저장소. 크래시 때 노드 객체(메모리 상태)는 버려지지만 Storage는 살아남는다.
 * Raft의 currentTerm/votedFor, Paxos의 accepted 값처럼 "디스크에 남겨야 하는 상태"를 표현한다.
 */
public interface Storage {

    void put(String key, Object value);

    /** 없으면 null. */
    Object get(String key);

    <T> T getOrDefault(String key, T defaultValue);
}
