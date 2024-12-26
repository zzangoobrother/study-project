package com.example.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String loginId;

    private String userName;

    public UserEntity(String loginId, String userName) {
        this.loginId = loginId;
        this.userName = userName;
    }

    public void modifyUser(String userName) {
        this.userName = userName;
    }
}
