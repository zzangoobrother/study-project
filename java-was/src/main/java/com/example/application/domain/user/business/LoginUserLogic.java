package com.example.application.domain.user.business;

import com.example.application.database.UserVO;
import com.example.application.database.dao.UserDao;
import com.example.application.mapper.UserMapper;
import com.example.application.domain.user.model.User;
import com.example.application.processor.Triggerable;
import com.example.application.domain.user.request.LoginRequest;

public class LoginUserLogic implements Triggerable<LoginRequest, User> {

    private final UserDao userDao;

    public LoginUserLogic(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public User run(LoginRequest loginRequest) {
        return login(loginRequest);
    }

    private User login(LoginRequest loginRequest) {
        String userId = loginRequest.getUserId();
        String password = loginRequest.getPassword();

        UserVO userVO = userDao.findByUsername(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 아이디를 가진 사용자가 존재하지 않습니다."));

        User user = UserMapper.toUser(userVO);
        if (!user.getPassword().equals(password)) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        return user;
    }
}
