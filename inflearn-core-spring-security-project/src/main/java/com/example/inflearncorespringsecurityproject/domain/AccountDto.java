package com.example.inflearncorespringsecurityproject.domain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountDto {
    private Long id;
    private String username;
    private String password;
    private String email;
    private String age;
    private String role;

    public AccountDto(Long id, String username, String password, String email, String age, String role) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.email = email;
        this.age = age;
        this.role = role;
    }
}
