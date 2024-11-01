package com.example.dto.response;

import org.springframework.http.HttpStatus;

public record CommonResponse<T>(
        HttpStatus status,
        String code,
        String message,
        T requestBody
) {
}
