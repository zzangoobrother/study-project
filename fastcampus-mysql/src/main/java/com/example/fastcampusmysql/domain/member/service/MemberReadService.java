package com.example.fastcampusmysql.domain.member.service;

import com.example.fastcampusmysql.domain.member.dto.MemberDto;
import com.example.fastcampusmysql.domain.member.entity.Member;
import com.example.fastcampusmysql.domain.member.repository.MemberRepository;
import org.springframework.stereotype.Service;

@Service
public class MemberReadService {

    private final MemberRepository memberRepository;

    public MemberReadService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public MemberDto getMember(Long id) {
        return toDto(memberRepository.findById(id).orElseThrow());
    }

    public MemberDto toDto(Member member) {
        return new MemberDto(member.getId(), member.getEmail(), member.getNickname(), member.getBirthday());
    }
}
