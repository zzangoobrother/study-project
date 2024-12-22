package com.example.application.database;

import com.example.application.domain.user.model.User;

import java.util.concurrent.ConcurrentHashMap;

public class UserDatabase extends Database<User> {
    private final ConcurrentHashMap<String, Object> unique = new ConcurrentHashMap<>();
    private static final Object PRESENT = new Object();

    @Override
    public long save(User user) {
        Object value = unique.putIfAbsent(user.getUserId(), PRESENT);
        if (value != null) {
            throw new IllegalArgumentException("이미 존재하는 userId 입니다.");
        }

        return super.save(user);
    }
}
