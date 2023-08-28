package com.example.testcodewitharchitecture.user.service.port;

import com.example.testcodewitharchitecture.user.domain.User;
import com.example.testcodewitharchitecture.user.domain.UserStatus;

import java.util.Optional;

public interface UserRepository {
    User getById(long id);

    Optional<User> findById(long id);

    Optional<User> findByIdAndStatus(long id, UserStatus status);

    Optional<User> findByEmailAndStatus(String email, UserStatus status);

    User save(User user);
}
