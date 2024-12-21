package com.example.model;

public class User {

    private Long userId;
    private String email;
    private String password;
    private String name;
    private String nickname;

    public User(String email, String password, String name, String nickname) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.nickname = nickname;
    }

    public void initUserPk(Long userPk) {
        if (this.userId != null) {
            throw new IllegalArgumentException("이미 PK가 존재합니다.");
        }

        this.userId = userPk;
    }

    public String getEmail() {
        return email;
    }

    public Long getUserId() {
        return userId;
    }

    public String getPassword() {
        return password;
    }

    public String getName() {
        return name;
    }

    public String getNickname() {
        return nickname;
    }

    @Override
    public String toString() {
        return "User{" +
                "email='" + email + '\'' +
                ", userId='" + userId + '\'' +
                ", password='" + password + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
