package com.example.controller.dto;

public record CustomerRegisterDTO(
        String username,
        String phoneNumber,
        int age,
        String address,
        String email,
        String password1,
        String password2
) {
}
