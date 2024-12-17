package com.example.model.business.user;

import com.example.database.Database;
import com.example.model.User;
import com.example.processor.Triggerable;
import com.example.web.user.response.UserListResponse;

import java.util.ArrayList;
import java.util.List;

public class GetUserListLogic implements Triggerable<Void, UserListResponse> {

    private final Database<User> userDatabase;

    public GetUserListLogic(Database<User> userDatabase) {
        this.userDatabase = userDatabase;
    }

    @Override
    public UserListResponse run(Void unused) {
        return getUserList();
    }

    public UserListResponse getUserList() {
        List<User> userList = new ArrayList<>(userDatabase.findAll());
        return UserListResponse.of(userList);
    }
}
