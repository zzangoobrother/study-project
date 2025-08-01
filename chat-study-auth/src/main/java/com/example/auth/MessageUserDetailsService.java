package com.example.auth;

import com.example.entity.UserEntity;
import com.example.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(MessageUserDetailsService.class);
    private final UserRepository userRepository;

    public MessageUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity userEntity = userRepository.findByUsername(username).orElseThrow(() -> {
            log.info("User not found : {}", username);
            return new UsernameNotFoundException("");
        });

        return new MessageUserDetails(userEntity.getUserId(), userEntity.getUsername(), userEntity.getPassword());
    }
}
