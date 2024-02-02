package com.example.fastcampusmysql.domain.member.service;

import com.example.fastcampusmysql.domain.member.dto.RegisterMemberCommand;
import com.example.fastcampusmysql.domain.member.entity.Member;
import com.example.fastcampusmysql.domain.member.repository.MemberRepository;
import org.springframework.stereotype.Service;

@Service
public class MemberWriteService {

    private final MemberRepository memberRepository;

    public MemberWriteService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public Member register(RegisterMemberCommand command) {
        /*
            회원 정보 등록
            이메일, 닉네임, 생년월일
            닉네임은 10자를 넘길 수 없다.
         */
        var member = Member.builder()
                .nickname(command.nickname())
                .birthday(command.birthday())
                .email(command.email())
                .build();

        return memberRepository.save(member);
    }
}
