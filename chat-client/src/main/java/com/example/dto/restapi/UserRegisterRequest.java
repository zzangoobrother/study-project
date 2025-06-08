package com.example.dto.restapi;

public record UserRegisterRequest(
        String username,
        String password
) {
}
