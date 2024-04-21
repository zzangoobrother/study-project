package com.example.inflearncorespringsecurityproject.domain;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AccountDto {
    private Long id;
    private String username;
    private String password;
    private String email;
    private String age;
    private String role;
}
