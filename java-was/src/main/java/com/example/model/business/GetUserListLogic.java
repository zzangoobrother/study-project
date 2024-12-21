package com.example.model.business;

import com.example.database.dao.UserDao;
import com.example.mapper.UserMapper;
import com.example.model.User;
import com.example.processor.Triggerable;
import com.example.web.user.response.UserListResponse;

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
