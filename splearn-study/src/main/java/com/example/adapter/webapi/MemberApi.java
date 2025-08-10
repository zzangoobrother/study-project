package com.example.adapter.webapi;

import com.example.adapter.webapi.dto.MemberRegisterResponse;
import com.example.application.member.provided.MemberRegister;
import com.example.domain.member.Member;
import com.example.domain.member.MemberRegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class MemberApi {

    private final MemberRegister memberRegister;

    @PostMapping("/api/members")
    public MemberRegisterResponse register(@RequestBody @Valid MemberRegisterRequest request) {
        Member member = memberRegister.register(request);
        return MemberRegisterResponse.of(member);
    }
}
