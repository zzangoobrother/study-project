package com.example.studylocalcache.dto;

import lombok.Getter;

@Getter
public class SignupResponse {
    private String loginId;
    private String name;
    private int age;

    public SignupResponse(String loginId, String name, int age) {
        this.loginId = loginId;
        this.name = name;
        this.age = age;
    }
}
