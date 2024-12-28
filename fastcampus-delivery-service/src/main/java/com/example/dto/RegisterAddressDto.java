package com.example.dto;

public record RegisterAddressDto(
        Long userId,
        String address,
        String alias
) {
}
