package com.example.service;

import com.example.constants.KeyPrefix;
import com.example.dto.domain.InviteCode;
import com.example.dto.domain.UserId;
import com.example.dto.projection.CountProjection;
import com.example.dto.projection.UsernameProjection;
import com.example.entity.User;
import com.example.entity.UserEntity;
import com.example.repository.UserRepository;
import com.example.util.JsonUtil;
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

    private final long TTL = 3600;

    private final SessionService sessionService;
    private final CacheService cacheService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JsonUtil jsonUtil;

    public UserService(SessionService sessionService, CacheService cacheService, UserRepository userRepository, PasswordEncoder passwordEncoder, JsonUtil jsonUtil) {
        this.sessionService = sessionService;
        this.cacheService = cacheService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jsonUtil = jsonUtil;
    }

    @Transactional(readOnly = true)
    public Optional<String> getUsername(UserId userId) {
        String key = cacheService.buildKey(KeyPrefix.USERNAME, userId.id().toString());
        Optional<String> cachedUsername = cacheService.get(key);
        if (cachedUsername.isPresent()) {
            return cachedUsername;
        }

        Optional<String> fromDb = userRepository.findByUserId(userId.id()).map(UsernameProjection::getUsername);
        fromDb.ifPresent(username -> cacheService.set(key, username, TTL));
        return fromDb;
    }

    @Transactional(readOnly = true)
    public Optional<UserId> getUserId(String username) {
        String key = cacheService.buildKey(KeyPrefix.USER_ID, username);
        Optional<String> cachedUserId = cacheService.get(key);
        if (cachedUserId.isPresent()) {
            return Optional.of(new UserId(Long.valueOf(cachedUserId.get())));
        }

        Optional<UserId> fromDb = userRepository.findUserIdByUsername(username)
                .map(projection -> new UserId(projection.getUserId()));
        fromDb.ifPresent(userId -> cacheService.set(key, userId.id().toString(), TTL));
        return fromDb;
    }

    @Transactional(readOnly = true)
    public List<UserId> getUserIds(List<String> usernames) {
        return userRepository.findByUsernameIn(usernames).stream()
                .map(projection -> new UserId(projection.getUserId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<User> getUser(InviteCode inviteCode) {
        String key = cacheService.buildKey(KeyPrefix.USER, inviteCode.code());
        Optional<String> cachedUser = cacheService.get(key);
        if (cachedUser.isPresent()) {
            return jsonUtil.fromJson(cachedUser.get(), User.class);
        }

        Optional<User> fromDb = userRepository.findByInviteCode(inviteCode.code()).map(entity -> new User(new UserId(entity.getUserId()), entity.getUsername()));
        fromDb.flatMap(jsonUtil::toJson).ifPresent(json -> cacheService.set(key, json, TTL));
        return fromDb;
    }

    @Transactional(readOnly = true)
    public Optional<InviteCode> getInviteCode(UserId userId) {
        String key = cacheService.buildKey(KeyPrefix.USER_INVITECODE, userId.id().toString());
        Optional<String> cachedInviteCode = cacheService.get(key);
        if (cachedInviteCode.isPresent()) {
            return Optional.of(new InviteCode(cachedInviteCode.get()));
        }

        Optional<InviteCode> fromDb = userRepository.findInviteCodeByUserId(userId.id())
                .map(inviteCode -> new InviteCode(inviteCode.getInviteCode()));
        fromDb.ifPresent(inviteCode -> cacheService.set(key, inviteCode.code(), TTL));
        return fromDb;
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
        String userId = userEntity.getUserId().toString();

        cacheService.delete(
                List.of(
                        cacheService.buildKey(KeyPrefix.USER_ID, username),
                        cacheService.buildKey(KeyPrefix.USERNAME, userId),
                        cacheService.buildKey(KeyPrefix.USER, userId),
                        cacheService.buildKey(KeyPrefix.USER_INVITECODE, userId)
                )
        );

        log.info("User unregistered. UserId : {}, Username : {}", userEntity.getUserId(), userEntity.getUsername());
    }
}
