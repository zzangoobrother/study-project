package com.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
@Entity
public class User extends BaseEntity {

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "invite_code", nullable = false)
    private String inviteCode;

    @Column(name = "connection_count", nullable = false)
    private int connectionCount;

    @Builder
    public User(String username, String password) {
        this.username = username;
        this.password = password;

        this.inviteCode = UUID.randomUUID().toString().replace("-", "");
        this.connectionCount = 0;
    }
}
