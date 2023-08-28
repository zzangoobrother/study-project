package com.example.testcodewitharchitecture.user.infrastructure;

import com.example.testcodewitharchitecture.user.domain.User;
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
    public Optional<User> findById(long id) {
        return jpaUserRepository.findById(id)
                .map(UserEntity::toModel);
    }

    @Override
    public Optional<User> findByIdAndStatus(long id, UserStatus status) {
        return jpaUserRepository.findByIdAndStatus(id, status).map(UserEntity::toModel);
    }

    @Override
    public Optional<User> findByEmailAndStatus(String email, UserStatus status) {
        return jpaUserRepository.findByEmailAndStatus(email, status).map(UserEntity::toModel);
    }

    @Override
    public User save(User user) {
        return jpaUserRepository.save(UserEntity.fromModel(user)).toModel();
    }
}
