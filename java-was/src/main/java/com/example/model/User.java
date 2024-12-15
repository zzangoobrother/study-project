package com.example.model;

public class User {

    private Long userPk;
    private String email;
    private String userId;
    private String password;
    private String name;

    public User(String email, String userId, String password, String name) {
        this.email = email;
        this.userId = userId;
        this.password = password;
        this.name = name;
    }

    public void initUserPk(long userPk) {
        if (this.userPk != null) {
            throw new IllegalArgumentException("이미 PK가 존재합니다.");
        }

        this.userPk = userPk;
    }

    public String getEmail() {
        return email;
    }

    public String getUserId() {
        return userId;
    }

    public String getPassword() {
        return password;
    }

    public String getName() {
        return name;
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
