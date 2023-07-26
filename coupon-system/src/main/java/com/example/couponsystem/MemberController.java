package com.example.couponsystem;

import com.example.couponsystem.domain.Member;
import com.example.couponsystem.domain.MemberRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemberController {

    private final MemberRepository memberRepository;

    public MemberController(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @GetMapping("/test")
    public void test() {
        memberRepository.save(new Member(10000));
    }
}
