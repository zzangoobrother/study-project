package com.example.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
@Entity
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String password;
    private String role;

    @Setter
    @Column(length = 500)
    private String refreshToken;

    @Builder
    public User(String username, String password, String role, String refreshToken) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.refreshToken = refreshToken;
    }

    public User(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }
}
