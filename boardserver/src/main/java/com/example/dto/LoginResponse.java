package com.example.dto;

public record LoginResponse(
        LoginStatus result,
        UserDTO userDTO
) {
    public static LoginResponse success(UserDTO userInfo) {
        return new LoginResponse(LoginStatus.SUCCESS, userInfo);
    }

    enum LoginStatus {
        SUCCESS, FAIL, DELETED
    }
}
