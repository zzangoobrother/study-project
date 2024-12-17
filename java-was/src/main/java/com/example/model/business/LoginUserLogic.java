package com.example.model.business;

import com.example.database.Database;
import com.example.model.User;
import com.example.processor.Triggerable;
import com.example.web.user.request.LoginRequest;

public class LoginUserLogic implements Triggerable<LoginRequest, User> {

    private final Database<User> userDatabase;

    public LoginUserLogic(Database<User> userDatabase) {
        this.userDatabase = userDatabase;
    }

    @Override
    public User run(LoginRequest loginRequest) {
        return login(loginRequest);
    }

    private User login(LoginRequest loginRequest) {
        String userId = loginRequest.getUserId();
        String password = loginRequest.getPassword();

        User user = userDatabase.findByCondition(it -> it.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("해당 아이디를 가진 사용자가 존재하지 않습니다."));

        if (!user.getPassword().equals(password)) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        return user;
    }
}
