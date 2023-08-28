package com.example.testcodewitharchitecture.user.infrastructure;

import com.example.testcodewitharchitecture.user.domain.UserStatus;
import com.example.testcodewitharchitecture.user.service.port.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepositoryImpl implements UserRepository {

    private final JpaUserRepository jpaUserRepository;

    public UserRepositoryImpl(JpaUserRepository jpaUserRepository) {
        this.jpaUserRepository = jpaUserRepository;
    }

    @Override
    public Optional<UserEntity> findById(long id) {
        return jpaUserRepository.findById(id);
    }

    @Override
    public Optional<UserEntity> findByIdAndStatus(long id, UserStatus status) {
        return jpaUserRepository.findByIdAndStatus(id, status);
    }

    @Override
    public Optional<UserEntity> findByEmailAndStatus(String email, UserStatus status) {
        return jpaUserRepository.findByEmailAndStatus(email, status);
    }

    @Override
    public UserEntity save(UserEntity userEntity) {
        return jpaUserRepository.save(userEntity);
    }
}
