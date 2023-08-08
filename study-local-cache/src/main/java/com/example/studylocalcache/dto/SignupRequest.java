package com.example.studylocalcache.dto;

import lombok.Getter;

@Getter
public class SignupRequest {
    private String loginId;
    private String password;
    private String name;
    private int age;
}
