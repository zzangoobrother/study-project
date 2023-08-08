package com.example.studylocalcache.service;

import com.example.studylocalcache.domain.Member;
import com.example.studylocalcache.domain.MemberRepository;
import com.example.studylocalcache.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;

@Slf4j
@Transactional
@Service
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public SignupResponse signup(SignupRequest request) {
        Member member = Member.builder()
                .loginId(request.getLoginId())
                .password(request.getPassword())
                .name(request.getName())
                .age(request.getAge())
                .build();

        Member saveMember = memberRepository.save(member);

        return new SignupResponse(saveMember.getLoginId(), saveMember.getName(), saveMember.getAge());
    }

    public void login(LoginRequest request) {
        Member member = memberRepository.findByLoginId(request.getLoginId()).orElseThrow(() -> new EntityNotFoundException());

        if (!request.getPassword().equals(member.getPassword())) {
            throw new IllegalStateException();
        }
    }

    @Cacheable(value = "member::get", key = "#loginId")
    @Transactional(readOnly = true)
    public MemberResponse getMember(String loginId) {
        Member member = memberRepository.findByLoginId(loginId).orElseThrow(() -> new EntityNotFoundException());

        return new MemberResponse(member.getLoginId(), member.getName(), member.getAge());
    }

    @CachePut(value = "member::get", key = "#loginId")
    public MemberResponse modifyMember(String loginId, ModifyRequest request) {
        Member member = memberRepository.findByLoginId(loginId).orElseThrow(() -> new EntityNotFoundException());

        member.modify(request.getName(), request.getAge());
        return new MemberResponse(member.getLoginId(), member.getName(), member.getAge());
    }
}
