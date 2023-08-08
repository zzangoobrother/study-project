package com.example.studylocalcache.api;

import com.example.studylocalcache.dto.*;
import com.example.studylocalcache.service.MemberService;
import org.springframework.web.bind.annotation.*;

@RestController
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping("/api/signup")
    public SignupResponse signup(@RequestBody SignupRequest request) {
        return memberService.signup(request);
    }

    @GetMapping("/api/login")
    public void login(@RequestBody LoginRequest request) {
        memberService.login(request);
    }

    @GetMapping("/api/members/{loginId}")
    public MemberResponse getMember(@PathVariable String loginId) {
        return memberService.getMember(loginId);
    }
}
