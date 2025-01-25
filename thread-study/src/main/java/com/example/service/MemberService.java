package com.example.service;

import com.example.dto.MemberRequest;
import com.example.entity.Member;
import com.example.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public Member getMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow();
    }

    @Transactional
    public Member create(MemberRequest request) {
        return memberRepository.save(new Member(request.name(), request.age()));
    }
}
