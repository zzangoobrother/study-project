package com.example.service;

import com.example.dto.domain.InviteCode;
import com.example.dto.domain.UserId;
import com.example.dto.projection.CountProjection;
import com.example.dto.projection.UsernameProjection;
import com.example.entity.User;
import com.example.entity.UserEntity;
import com.example.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final SessionService sessionService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(SessionService sessionService, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.sessionService = sessionService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Optional<String> getUsername(UserId userId) {
        return userRepository.findByUserId(userId.id()).map(UsernameProjection::getUsername);
    }

    @Transactional(readOnly = true)
    public Optional<UserId> getUserId(String username) {
        return userRepository.findUserIdByUsername(username)
                .map(projection -> new UserId(projection.getUserId()));
    }

    @Transactional(readOnly = true)
    public List<UserId> getUserIds(List<String> usernames) {
        return userRepository.findByUsernameIn(usernames).stream()
                .map(projection -> new UserId(projection.getUserId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<User> getUser(InviteCode inviteCode) {
        return userRepository.findByInviteCode(inviteCode.code()).map(entity -> new User(new UserId(entity.getUserId()), entity.getUsername()));
    }

    @Transactional(readOnly = true)
    public Optional<InviteCode> getInviteCode(UserId userId) {
        return userRepository.findInviteCodeByUserId(userId.id())
                .map(inviteCode -> new InviteCode(inviteCode.getInviteCode()));
    }

    @Transactional(readOnly = true)
    public Optional<Integer> getConnectionCount(UserId userId) {
        return userRepository.findCountByUserId(userId.id())
                .map(CountProjection::getConnectionCount);
    }

    @Transactional
    public UserId addUser(String username, String password) {
        UserEntity messageUser = userRepository.save(new UserEntity(username, passwordEncoder.encode(password)));
        log.info("User registered. UserId : {}, Username : {}", messageUser.getUserId(), messageUser.getUsername());

        return new UserId(messageUser.getUserId());
    }

    @Transactional
    public void removeUser() {
        String username = sessionService.getUsername();
        UserEntity userEntity = userRepository.findByUsername(username).orElseThrow();
        userRepository.deleteById(userEntity.getUserId());
        log.info("User unregistered. UserId : {}, Username : {}", userEntity.getUserId(), userEntity.getUsername());
    }
}
