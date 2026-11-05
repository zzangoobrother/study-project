package com.example.consensus.raft;

/**
 * 로그 엔트리 하나. 로그에서의 위치(index)는 1부터 시작하며 엔트리가 따로 갖지 않는다.
 * term은 리더가 이 엔트리를 만든 임기이고, command는 상태 머신이 적용할 명령이다.
 */
public record LogEntry(long term, String command) {

    @Override
    public String toString() {
        return command + "@" + term;
    }
}
