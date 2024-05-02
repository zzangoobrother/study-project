package com.example.inflearncorespringsecurityproject.domain.dto;

import lombok.*;

import java.util.List;

@Setter
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountDto {
    private String id;
    private String username;
    private String email;
    private Integer age;
    private String password;
    private List<String> roles;

    @Builder
    public AccountDto(String id, String username, String email, Integer age, String password, List<String> roles) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.age = age;
        this.password = password;
        this.roles = roles;
    }
}
