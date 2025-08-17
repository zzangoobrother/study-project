package com.example.dto;

public record LoginResponseDTO(
        String accessToken,
        String refreshToken
) {
}
