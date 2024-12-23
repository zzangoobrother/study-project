package com.example.application.database.vo;

import java.time.LocalDateTime;

public record UserVO(
        Long userId,
        String username,
        String password,
        String nickname,
        String email,
        LocalDateTime createdAt
) {
}
