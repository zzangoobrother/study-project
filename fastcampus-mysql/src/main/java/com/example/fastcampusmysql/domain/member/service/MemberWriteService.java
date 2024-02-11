package com.example.fastcampusmysql.domain.member.service;

import com.example.fastcampusmysql.domain.member.dto.RegisterMemberCommand;
import com.example.fastcampusmysql.domain.member.entity.Member;
import com.example.fastcampusmysql.domain.member.entity.MemberNicknameHistory;
import com.example.fastcampusmysql.domain.member.repository.MemberNicknameHistoryRepository;
import com.example.fastcampusmysql.domain.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberWriteService {

    private final MemberRepository memberRepository;
    private final MemberNicknameHistoryRepository memberNicknameHistoryRepository;

    public MemberWriteService(MemberRepository memberRepository, MemberNicknameHistoryRepository memberNicknameHistoryRepository) {
        this.memberRepository = memberRepository;
        this.memberNicknameHistoryRepository = memberNicknameHistoryRepository;
    }

    @Transactional
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

        var saveMember = memberRepository.save(member);

        saveMemberNicknameHistroy(member.getId(), member.getNickname());

        return saveMember;
    }

    @Transactional
    public void cheangeNickname(Long memberId, String nickname) {
        var member = memberRepository.findById(memberId).orElseThrow();

        member.changeNickname(nickname);
        memberRepository.save(member);

        saveMemberNicknameHistroy(memberId, member.getNickname());
    }

    private void saveMemberNicknameHistroy(Long memberId, String nickname) {
        var history = MemberNicknameHistory.builder()
                .memberId(memberId)
                .nickname(nickname)
                .build();
        memberNicknameHistoryRepository.save(history);
    }
}
