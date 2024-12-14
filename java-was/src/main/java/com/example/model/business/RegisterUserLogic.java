package com.example.model.business;

import com.example.model.User;
import com.example.processor.Triggerable;
import com.example.web.user.RegisterRequest;

public class RegisterUserLogic implements Triggerable<RegisterRequest, Void> {

    public void registerUser(RegisterRequest registerRequest) {
        String email = registerRequest.getEmail();
        String userId = registerRequest.getUserId();
        String password = registerRequest.getPassword();
        String name = registerRequest.getName();

        User user = new User(email, userId, password, name);
        System.out.println("user = " + user);
    }

    @Override
    public Void run(RegisterRequest registerRequest) {
        registerUser(registerRequest);
        return null;
    }
}
