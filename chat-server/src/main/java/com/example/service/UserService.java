package com.example.service;

import com.example.domain.User;
import com.example.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class UserService {

    private final SessionService sessionService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public void register(String username, String password) {
        userRepository.save(new User(username, passwordEncoder.encode(password)));
    }

    @Transactional
    public void unregister() {
        String username = sessionService.getUsername();
        User user = userRepository.findByUsername(username).orElseThrow();
        userRepository.deleteById(user.getId());
    }
}
