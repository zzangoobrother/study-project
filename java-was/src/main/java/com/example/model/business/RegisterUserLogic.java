package com.example.model.business;

import com.example.database.dao.UserDao;
import com.example.model.User;
import com.example.processor.Triggerable;
import com.example.web.user.request.RegisterRequest;

public class RegisterUserLogic implements Triggerable<RegisterRequest, Long> {

    private final UserDao userDao;

    public RegisterUserLogic(UserDao userDao) {
        this.userDao = userDao;
    }

    public Long registerUser(RegisterRequest registerRequest) {
        String email = registerRequest.getEmail();
        String userId = registerRequest.getUserId();
        String password = registerRequest.getPassword();
        String name = registerRequest.getName();

        User user = new User(email, userId, password, name);
        long savePk = userDao.save(user);
        user.initUserPk(savePk);

        return savePk;
    }

    @Override
    public Long run(RegisterRequest registerRequest) {
        return registerUser(registerRequest);
    }
}
