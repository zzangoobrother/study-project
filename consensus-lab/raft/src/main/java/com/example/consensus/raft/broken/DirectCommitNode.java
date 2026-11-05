package com.example.consensus.raft.broken;

import com.example.consensus.raft.RaftConfig;
import com.example.consensus.raft.RaftMonitor;
import com.example.consensus.raft.RaftNode;
import com.example.consensus.raft.RaftWorkload;

/**
 * 틀린 변형 2: 이전 임기 엔트리도 과반 복제만으로 직접 커밋한다.
 *
 * <p>빼먹은 것: "log[N].term == currentTerm인 N만 커밋한다"는 조건(논문 5.4.2).
 * 과반에 복제되었다고 해서 그 엔트리가 안전한 것은 아니다. 이전 임기의 엔트리는 과반에 있어도
 * 더 높은 임기의 서버가 뽑혀 덮어쓸 수 있다(논문 그림 8). 이미 커밋하고 상태 머신에 적용한 뒤라면 되돌릴 수 없다.
 */
public class DirectCommitNode extends RaftNode {

    public DirectCommitNode(RaftConfig config, RaftMonitor monitor, RaftWorkload workload) {
        super(config, monitor, workload);
    }

    @Override
    protected boolean mayCommit(int index) {
        return true;
    }
}
