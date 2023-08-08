package com.example.studylocalcache.dto;

import lombok.Getter;

@Getter
public class MemberResponse {
    private String loginId;
    private String name;
    private int age;

    public MemberResponse(String loginId, String name, int age) {
        this.loginId = loginId;
        this.name = name;
        this.age = age;
    }
}
