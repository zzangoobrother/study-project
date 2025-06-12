package com.example.entity;

import com.example.dto.domain.UserId;

import java.util.Objects;

public record User(
        UserId userId,
        String username
) {

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        User user = (User) object;
        return Objects.equals(userId, user.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
}
