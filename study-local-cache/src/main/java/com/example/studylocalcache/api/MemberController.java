package com.example.studylocalcache.api;

import com.example.studylocalcache.dto.LoginRequest;
import com.example.studylocalcache.dto.SignupRequest;
import com.example.studylocalcache.dto.SignupResponse;
import com.example.studylocalcache.service.MemberService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
}
