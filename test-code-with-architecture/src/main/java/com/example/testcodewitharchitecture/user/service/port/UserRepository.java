package com.example.testcodewitharchitecture.user.service.port;

import com.example.testcodewitharchitecture.user.domain.UserStatus;
import com.example.testcodewitharchitecture.user.infrastructure.UserEntity;

import java.util.Optional;

public interface UserRepository {
    Optional<UserEntity> findById(long id);

    Optional<UserEntity> findByIdAndStatus(long id, UserStatus status);

    Optional<UserEntity> findByEmailAndStatus(String email, UserStatus status);

    UserEntity save(UserEntity userEntity);
}
