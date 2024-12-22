package com.example.application.domain.user.request;

public class RegisterRequest {

    private String email;
    private String userId;
    private String password;
    private String name;

    public RegisterRequest(String email, String userId, String password, String name) {
        this.email = email;
        this.userId = userId;
        this.password = password;
        this.name = name;
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
}
