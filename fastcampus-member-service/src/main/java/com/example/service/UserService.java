package com.example.service;

import com.example.entity.UserEntity;
import com.example.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserEntity registerUser(String loginId, String userName) {
        UserEntity user = new UserEntity(loginId, userName);
        return userRepository.save(user);
    }

    public UserEntity modifyUser(Long userId, String userName) {
        UserEntity user = userRepository.findById(userId).orElseThrow();
        user.modifyUser(userName);

        return userRepository.save(user);
    }

    public UserEntity getUser(String loginId) {
        return userRepository.findByLoginId(loginId).orElseThrow();
    }
}
