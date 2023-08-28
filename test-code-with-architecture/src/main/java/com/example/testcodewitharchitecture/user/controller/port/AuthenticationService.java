package com.example.testcodewitharchitecture.user.controller.port;

public interface AuthenticationService {

    void login(long id);

    void verifyEmail(long id, String certificationCode);
}
