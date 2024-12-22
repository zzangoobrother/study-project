package com.example.application.domain.user.response;

public class UserInfoResponse {

    private final String name;

    public UserInfoResponse(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
