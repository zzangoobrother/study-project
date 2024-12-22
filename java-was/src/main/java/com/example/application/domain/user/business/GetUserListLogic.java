package com.example.application.domain.user.business;

import com.example.application.database.dao.UserDao;
import com.example.application.mapper.UserMapper;
import com.example.application.domain.user.model.User;
import com.example.application.processor.Triggerable;
import com.example.application.domain.user.response.UserListResponse;

import java.util.List;

public class GetUserListLogic implements Triggerable<Void, UserListResponse> {

    private final UserDao userDao;

    public GetUserListLogic(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public UserListResponse run(Void unused) {
        return getUserList();
    }

    public UserListResponse getUserList() {
        List<User> userList = userDao.findAll().stream()
                .map(UserMapper::toUser)
                .toList();
        return UserListResponse.of(userList);
    }
}
