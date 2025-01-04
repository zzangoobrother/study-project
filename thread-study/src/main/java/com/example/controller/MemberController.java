package com.example.controller;

import com.example.dto.MemberRequest;
import com.example.entity.Member;
import com.example.service.MemberService;
import org.springframework.web.bind.annotation.*;

@RestController
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/api/v1/members")
    public Member getMember(@RequestParam Long memberId) {
        return memberService.getMember(memberId);
    }

    @PostMapping("/api/v1/members")
    public Member create(@RequestBody MemberRequest request) {
        return memberService.create(request);
    }
}
