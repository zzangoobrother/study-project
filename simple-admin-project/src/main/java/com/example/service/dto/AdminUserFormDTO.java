package com.example.service.dto;

public record AdminUserFormDTO(
        String username,
        String password1,
        String password2,
        String email
) {
}
