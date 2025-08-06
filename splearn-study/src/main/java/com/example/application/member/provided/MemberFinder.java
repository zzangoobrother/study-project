package com.example.application.member.provided;

import com.example.domain.member.Member;

public interface MemberFinder {
    Member find(Long memberId);
}
