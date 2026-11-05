package com.example.consensus.raft.broken;

import com.example.consensus.raft.RaftConfig;
import com.example.consensus.raft.RaftMonitor;
import com.example.consensus.raft.RaftNode;
import com.example.consensus.raft.RaftWorkload;

/**
 * 틀린 변형 1: 투표 제한을 뺐다.
 *
 * <p>빼먹은 것: "후보의 로그가 내 로그만큼 최신일 때만 투표한다"는 검사(논문 5.4.1). term과 votedFor만 보고 표를 준다.
 * 과반에 복제되어 커밋된 엔트리를 가진 서버들이 있어도, 로그가 뒤처진 서버가 다른 서버들의 표를 모아 리더가 될 수 있고,
 * 새 리더는 자기 로그를 기준으로 팔로워 로그를 덮어쓰므로 커밋된 엔트리가 사라진다(Leader Completeness 위반).
 */
public class NoVoteRestrictionNode extends RaftNode {

    public NoVoteRestrictionNode(RaftConfig config, RaftMonitor monitor, RaftWorkload workload) {
        super(config, monitor, workload);
    }

    @Override
    protected boolean candidateLogIsUpToDate(long candidateLastLogTerm, int candidateLastLogIndex) {
        return true;
    }
}
