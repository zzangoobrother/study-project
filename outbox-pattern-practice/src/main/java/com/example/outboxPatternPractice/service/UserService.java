package com.example.outboxPatternPractice.service;

import com.example.outboxPatternPractice.entity.User;
import com.example.outboxPatternPractice.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Long register(String name) {
        return userRepository.save(new User(name)).getId();
    }
}
