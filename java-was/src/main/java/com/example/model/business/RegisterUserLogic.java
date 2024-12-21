package com.example.model.business;

import com.example.database.dao.UserDao;
import com.example.mapper.UserMapper;
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
        long savedPk = userDao.save(UserMapper.toUserVO(user));
        if (savedPk == -1) {
            throw new IllegalArgumentException("이미 존재하는 username 입니다.");
        }
        user.initUserPk(savedPk);

        return savedPk;
    }

    @Override
    public Long run(RegisterRequest registerRequest) {
        return registerUser(registerRequest);
    }
}
