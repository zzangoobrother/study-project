package com.example.application.member.provided;


import com.example.domain.member.Member;
import com.example.domain.member.MemberInfoUpdateRequest;
import com.example.domain.member.MemberRegisterRequest;
import jakarta.validation.Valid;

public interface MemberRegister {
    Member register(@Valid MemberRegisterRequest registerRequest);

    Member activate(Long memberId);

    Member deactivate(Long memberId);

    Member updateInfo(Long memberId, @Valid MemberInfoUpdateRequest memberInfoUpdateRequest);
}
