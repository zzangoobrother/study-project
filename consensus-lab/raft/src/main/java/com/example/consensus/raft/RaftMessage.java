package com.example.consensus.raft;

import java.util.List;

/**
 * Raft RPC 네 가지(RequestVote/AppendEntries 요청과 응답). Context.send는 참조를 그대로 넘기므로 모두 불변 record다.
 * 논문과 달리 candidateId/leaderId 필드는 없다. 발신자(from)를 시뮬레이터가 찍어 주기 때문이다.
 */
public sealed interface RaftMessage {

    /** "내 로그는 (lastLogTerm, lastLogIndex)까지다. term 임기의 리더로 뽑아 달라." */
    record RequestVote(long term, int lastLogIndex, long lastLogTerm) implements RaftMessage {}

    /** 응답한 쪽의 currentTerm과 투표 여부. 후보는 자기 term과 같은 응답만 센다. */
    record VoteResponse(long term, boolean granted) implements RaftMessage {}

    /**
     * 로그 복제 겸 하트비트(entries가 비면 하트비트). prevLogIndex/prevLogTerm은 "이 엔트리 바로 앞 엔트리"이며
     * 팔로워는 자기 로그에 같은 엔트리가 있을 때만 받아들인다.
     */
    record AppendEntries(long term, int prevLogIndex, long prevLogTerm, List<LogEntry> entries, int leaderCommit)
            implements RaftMessage {}

    /**
     * 논문의 응답(term, success)에 요청의 정보(requestTerm, prevLogIndex, entryCount)를 되돌려 준다.
     * 리더가 "어느 요청에 대한 응답인지" 알아야 지연·중복 응답이 nextIndex를 엉뚱하게 움직이지 않고,
     * 성공 시 matchIndex(= prevLogIndex + entryCount)를 응답만으로 계산할 수 있다.
     * requestTerm이 따로 필요한 이유: 임기가 낮은 요청을 거절한 응답은 term에 팔로워의 (더 높은) 임기를 싣는다.
     * 그 응답이 늦게 도착했을 때 리더가 그새 같은 임기로 올라와 있으면 term만으로는 옛 거절을 구별하지 못한다.
     */
    record AppendResponse(long term, boolean success, long requestTerm, int prevLogIndex, int entryCount) implements RaftMessage {}
}
