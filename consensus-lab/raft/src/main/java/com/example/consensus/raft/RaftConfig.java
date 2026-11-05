package com.example.consensus.raft;

/**
 * Raft 실험 설정. 시간 단위는 시뮬레이터의 가상 시간이다.
 *
 * @param electionTimeoutMin 선거 타임아웃 하한 (min == max면 난수를 쓰지 않는 고정값)
 * @param electionTimeoutMax 선거 타임아웃 상한. 타이머를 새로 걸 때마다 [min, max]에서 균등하게 뽑는다
 * @param heartbeatInterval  리더가 AppendEntries(하트비트)를 보내는 주기
 */
public record RaftConfig(long electionTimeoutMin, long electionTimeoutMax, long heartbeatInterval) {

    public RaftConfig {
        if (electionTimeoutMin <= 0 || electionTimeoutMax < electionTimeoutMin || heartbeatInterval <= 0) {
            throw new IllegalArgumentException("타임아웃 범위가 올바르지 않다");
        }
    }

    /** 논문 권장 비율을 따른 기본값: 선거 타임아웃 [150, 300], 하트비트 50. 네트워크 지연(1~10)보다 충분히 크다. */
    public static RaftConfig of() {
        return new RaftConfig(150, 300, 50);
    }

    public RaftConfig withElectionTimeout(long min, long max) {
        return new RaftConfig(min, max, heartbeatInterval);
    }

    public RaftConfig withHeartbeatInterval(long heartbeatInterval) {
        return new RaftConfig(electionTimeoutMin, electionTimeoutMax, heartbeatInterval);
    }
}
