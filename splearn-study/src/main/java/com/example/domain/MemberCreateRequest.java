package com.example.domain;

public record MemberCreateRequest(
        String email,
        String nickname,
        String password
) {
}
