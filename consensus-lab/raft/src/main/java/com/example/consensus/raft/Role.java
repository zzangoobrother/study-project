package com.example.consensus.raft;

/** 서버가 지금 맡은 역할. 논문 그림 4의 세 상태다. */
public enum Role {
    FOLLOWER,
    CANDIDATE,
    LEADER
}
