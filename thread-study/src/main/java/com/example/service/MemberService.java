package com.example.service;

import com.example.dto.MemberRequest;
import com.example.entity.Member;
import com.example.repository.MemberRepository;
import org.springframework.stereotype.Service;

@Service
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public Member getMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow();
    }

    public Member create(MemberRequest request) {
        return memberRepository.save(new Member(request.name(), request.age()));
    }
}
