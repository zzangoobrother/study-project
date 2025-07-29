package com.example.application.provided;


import com.example.domain.Member;
import com.example.domain.MemberRegisterRequest;

public interface MemberRegister {
    Member register(MemberRegisterRequest registerRequest);
}
