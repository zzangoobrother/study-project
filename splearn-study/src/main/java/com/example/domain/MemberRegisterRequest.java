package com.example.domain;

public record MemberRegisterRequest(
        String email,
        String nickname,
        String password
) {
}
