package com.example.model.business;

import com.example.database.Database;
import com.example.model.User;
import com.example.processor.Triggerable;
import com.example.web.user.RegisterRequest;

public class RegisterUserLogic implements Triggerable<RegisterRequest, Long> {

    private final Database<User> userDatabase;

    public RegisterUserLogic(Database<User> userDatabase) {
        this.userDatabase = userDatabase;
    }

    public Long registerUser(RegisterRequest registerRequest) {
        String email = registerRequest.getEmail();
        String userId = registerRequest.getUserId();
        String password = registerRequest.getPassword();
        String name = registerRequest.getName();

        User user = new User(email, userId, password, name);
        long savePk = userDatabase.save(user);
        user.initUserPk(savePk);

        return savePk;
    }

    @Override
    public Long run(RegisterRequest registerRequest) {
        return registerUser(registerRequest);
    }
}
