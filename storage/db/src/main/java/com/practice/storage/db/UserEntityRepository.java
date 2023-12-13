package com.practice.storage.db;

import com.practice.domain.user.User;
import com.practice.domain.user.UserRepository;
import org.springframework.stereotype.Repository;

@Repository
public class UserEntityRepository implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    public UserEntityRepository(UserJpaRepository userJpaRepository) {
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public Long add(String name) {
        UserEntity userEntity = new UserEntity(name);
        return userJpaRepository.save(userEntity).getId();
    }

    @Override
    public User read(Long userId) {
        UserEntity userEntity = userJpaRepository.findById(userId).get();

        return new User(userEntity.getId(), userEntity.getName());
    }
}
