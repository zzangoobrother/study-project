package com.example.application.provided;


import com.example.domain.Member;
import com.example.domain.MemberRegisterRequest;
import jakarta.validation.Valid;

public interface MemberRegister {
    Member register(@Valid MemberRegisterRequest registerRequest);

    Member activate(Long memberId);
}
