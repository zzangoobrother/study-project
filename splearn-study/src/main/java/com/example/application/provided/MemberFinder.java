package com.example.application.provided;

import com.example.domain.Member;

public interface MemberFinder {
    Member find(Long memberId);
}
