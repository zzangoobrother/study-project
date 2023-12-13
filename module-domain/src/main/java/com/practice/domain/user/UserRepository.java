package com.practice.domain.user;

public interface UserRepository {
    Long add(String name);
    User read(Long userId);
}
