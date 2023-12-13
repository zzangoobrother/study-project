package com.practice.domain.user;

import org.springframework.stereotype.Component;

@Component
public class UserReader {
    private final UserRepository userRepository;

    public UserReader(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User read(Long userId) {
        return userRepository.read(userId);
    }
}
