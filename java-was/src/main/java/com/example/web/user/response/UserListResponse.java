package com.example.web.user.response;

import com.example.model.User;

import java.util.List;

public class UserListResponse {

    private final int count;
    private final List<UserInfo> userList;

    private UserListResponse(List<User> users) {
        this.userList = users.stream()
                .map(it -> new UserInfo(it.getName(), it.getEmail()))
                .toList();
        this.count = userList.size();
    }

    public static UserListResponse of(List<User> users) {
        return new UserListResponse(users);
    }

    public int getCount() {
        return count;
    }

    public List<UserInfo> getUserList() {
        return userList;
    }

    public static class UserInfo {
        private final String name;
        private final String email;

        public UserInfo(String name, String email) {
            this.name = name;
            this.email = email;
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }
    }
}
