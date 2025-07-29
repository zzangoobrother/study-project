package com.example.application.required;

import com.example.domain.Member;
import org.springframework.data.repository.Repository;

public interface MemberRepository extends Repository<Member, Long> {
    Member save(Member member);
}
