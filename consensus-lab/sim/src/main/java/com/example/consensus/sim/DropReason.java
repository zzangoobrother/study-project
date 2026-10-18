package com.example.consensus.sim;

/** 메시지 또는 타이머가 버려진 이유. */
public enum DropReason {
    NETWORK,
    CRASHED,
    PARTITION
}
