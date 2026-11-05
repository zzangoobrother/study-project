package com.example.consensus.raft;

import static com.example.consensus.raft.RaftScenarios.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 올바른 구현은 무작위 크래시·재시작·분할·메시지 유실 아래에서도 모든 불변식을 지킨다. */
class RaftRandomFaultTest {

    @Test
    @DisplayName("정상 구현: 무작위 장애 시드 1,000개에서 불변식 위반 0")
    void correctImplementationNeverViolates() {
        Sweep sweep = sweep(1, 1_000, seed -> randomFaultRun(seed, RaftNode::new));
        System.out.println("[RAFT] 무작위 장애(정상 구현, 시드 1..1000, 5대, 유실 10%, 지연 1~10, 시각 6,000까지 크래시/재시작/분할 약 40회): "
                + sweep.summary());
        System.out.println(sweep.report());
        assertThat(sweep.report().violations()).isZero();
        // 장애가 너무 세서 아무 일도 안 일어난 실험이 아님을 확인한다.
        assertThat(sweep.seedsWithCommit()).isGreaterThan(900);
    }
}
