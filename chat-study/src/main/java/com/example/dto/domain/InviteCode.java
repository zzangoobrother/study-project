package com.example.dto.domain;

public record InviteCode(
        String code
) {

    public InviteCode {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("Invalid UserId");
        }
    }
}
