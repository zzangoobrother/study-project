package com.example.studylocalcache.service;

import com.example.studylocalcache.domain.Member;
import com.example.studylocalcache.domain.MemberRepository;
import com.example.studylocalcache.dto.LoginRequest;
import com.example.studylocalcache.dto.SignupRequest;
import com.example.studylocalcache.dto.SignupResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.persistence.EntityNotFoundException;

@Slf4j
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
}
