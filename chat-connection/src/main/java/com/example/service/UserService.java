package com.example.service;

import com.example.constants.KeyPrefix;
import com.example.constants.ResultType;
import com.example.dto.domain.InviteCode;
import com.example.dto.domain.User;
import com.example.dto.domain.UserId;
import com.example.dto.projection.CountProjection;
import com.example.dto.projection.UserIdUsernameProjection;
import com.example.dto.projection.UsernameProjection;
import com.example.repository.UserRepository;
import com.example.util.JsonUtil;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final long TTL = 3600;
    private final long LIMIT_FIND_COUNT = 100;

    private final CacheService cacheService;
    private final UserRepository userRepository;
    private final JsonUtil jsonUtil;

    public UserService(CacheService cacheService, UserRepository userRepository, JsonUtil jsonUtil) {
        this.cacheService = cacheService;
        this.userRepository = userRepository;
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
    public Pair<Map<UserId, String>, ResultType> getUsernames(Set<UserId> userIds) {
        if (userIds.size() > LIMIT_FIND_COUNT) {
            return Pair.of(Collections.emptyMap(), ResultType.OVER_LIMIT);
        }

        List<String> usernames = cacheService.get(
                userIds.stream()
                        .map(userId -> cacheService.buildKey(KeyPrefix.USERNAME, userId.id().toString()))
                        .toList()
        );

        Map<UserId, String> resultMap = new HashMap<>();
        Set<UserId> missingUserIds = new HashSet<>();

        int index = 0;
        for (UserId userId : userIds) {
            String username = usernames.get(index++);
            if (username != null) {
                resultMap.put(userId, username);
            } else {
                missingUserIds.add(userId);
            }
        }

        if (!missingUserIds.isEmpty()) {
            Map<UserId, String> userIdsAndUsernames = userRepository.findByUserIdIn(
                            missingUserIds.stream().map(UserId::id).collect(Collectors.toUnmodifiableSet())).stream()
                    .collect(Collectors.toMap(proj -> new UserId(proj.getUserId()), UserIdUsernameProjection::getUsername));

            resultMap.putAll(userIdsAndUsernames);
            cacheService.set(userIdsAndUsernames.entrySet().stream()
                    .collect(Collectors.toMap(entry -> cacheService.buildKey(KeyPrefix.USERNAME, entry.getKey().id().toString()), Map.Entry::getValue)), TTL);
        }

        return Pair.of(resultMap, ResultType.SUCCESS);
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
}
