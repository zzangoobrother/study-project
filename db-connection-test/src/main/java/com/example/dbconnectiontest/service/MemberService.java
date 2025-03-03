package com.example.dbconnectiontest.service;

import com.example.dbconnectiontest.model.Member;
import com.example.dbconnectiontest.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class MemberService {

    private final MemberRepository memberRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void test(Member member) {
        member = memberRepository.getLock(member.getId());

        member.updateName(member.getName() + " lock");
    }
}
