package com.example.dto.domain;

import com.example.constants.UserConnectionStatus;

public record Connection(
        String username,
        UserConnectionStatus status
) {
}
