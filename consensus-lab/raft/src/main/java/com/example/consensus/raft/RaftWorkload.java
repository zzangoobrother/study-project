package com.example.consensus.raft;

/**
 * 시뮬레이션 하나에 하나씩 만드는 "클라이언트 부하". 리더가 하트비트 때마다 이번 임기에 만든 미커밋 엔트리가
 * maxOutstanding개 미만이면 새 명령(c1, c2, ...)을 받아 로그에 붙인다.
 * 시각 기반 이벤트(Action)를 미리 깔아 둘 수 없는 스케줄러(FIFO, 무작위 순서)에서도 명령이 들어가게 하려는 장치다.
 * 현재 리더로 믿는 노드가 여럿이면(분할 중인 옛 리더) 각자 명령을 받으므로 "커밋 안 된 엔트리"도 자연스럽게 생긴다.
 */
public final class RaftWorkload {

    private final int total;
    private final int maxOutstanding;
    private int issued = 0;

    public RaftWorkload(int total, int maxOutstanding) {
        this.total = total;
        this.maxOutstanding = maxOutstanding;
    }

    /** 부하가 없는 설정. */
    public static RaftWorkload none() {
        return new RaftWorkload(0, 0);
    }

    int maxOutstanding() {
        return maxOutstanding;
    }

    /** 다음 명령을 발급한다. 다 썼으면 null. */
    String issue() {
        return issued < total ? "c" + (++issued) : null;
    }

    public int issued() {
        return issued;
    }
}
