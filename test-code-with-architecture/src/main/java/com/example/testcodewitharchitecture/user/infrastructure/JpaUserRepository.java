package com.example.testcodewitharchitecture.user.infrastructure;

import com.example.testcodewitharchitecture.user.domain.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaUserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByIdAndStatus(long id, UserStatus status);

    Optional<UserEntity> findByEmailAndStatus(String email, UserStatus status);
}
