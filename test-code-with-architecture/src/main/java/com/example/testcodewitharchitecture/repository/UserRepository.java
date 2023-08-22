package com.example.testcodewitharchitecture.repository;

import com.example.testcodewitharchitecture.model.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByIdAndStatus(long id, UserStatus status);

    Optional<UserEntity> findByEmailAndStatus(String email, UserStatus status);
}
