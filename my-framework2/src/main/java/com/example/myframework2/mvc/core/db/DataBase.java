package com.example.myframework2.mvc.core.db;

import com.example.myframework2.mvc.board.model.User;
import com.google.common.collect.Maps;

import java.util.Collection;
import java.util.Map;

public class DataBase {
    private static Map<String, User> users = Maps.newHashMap();

    public static void addUser(User user) {
        users.put(user.getUserId(), user);
    }

    public static User findUserById(String userId) {
        return users.get(userId);
    }

    public static Collection<User> findAll() {
        return users.values();
    }
}
