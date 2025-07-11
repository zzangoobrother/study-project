package com.example.dto.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public record InviteCode(
        @JsonValue String code
) {

    public InviteCode {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("Invalid UserId");
        }
    }
}
