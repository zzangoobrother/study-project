package com.example.service;

import com.example.constants.IdKey;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SessionService {
    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SessionRepository<? extends Session> httpSessionRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final long TTL = 300;

    public SessionService(SessionRepository<? extends Session> httpSessionRepository, StringRedisTemplate stringRedisTemplate) {
        this.httpSessionRepository = httpSessionRepository;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public List<UserId> getOnlineParticipantUSerIds(ChannelId channelId, List<UserId> userIds) {
        List<String> channelIdKeys = userIds.stream().map(this::buildChannelIdKey).toList();

        try {
            List<String> channelIds = stringRedisTemplate.opsForValue().multiGet(channelIdKeys);
            if (channelIds != null) {
                List<UserId> onlineParticipantUSerIds = new ArrayList<>(channelIds.size());
                String chId = channelId.toString();
                for (int idx = 0; idx < userIds.size(); idx++) {
                    String value = channelIds.get(idx);
                    onlineParticipantUSerIds.add(value != null && value.equals(chId) ? userIds.get(idx) : null);
                }

                return onlineParticipantUSerIds;
            }
        } catch (Exception ex) {
            log.error("Redis get failed. key : {}, cause : {}", channelIdKeys, ex.getMessage());
        }

        return Collections.emptyList();
    }

    public boolean setActiveChannel(UserId userId, ChannelId channelId) {
        String channelIdKey = buildChannelIdKey(userId);
        try {
            stringRedisTemplate.opsForValue().set(channelIdKey, channelId.id().toString(), TTL, TimeUnit.SECONDS);
            return true;
        } catch (Exception ex) {
            log.error("Redis set failed. key : {}, channelId : {}", channelIdKey, channelId);
            return false;
        }
    }

    public boolean removeActiveChannel(UserId userId) {
        String channelIdKey = buildChannelIdKey(userId);
        try {
            stringRedisTemplate.delete(channelIdKey);
            return true;
        } catch (Exception ex) {
            log.error("Redis set failed. key : {}", channelIdKey);
            return false;
        }
    }

    public void refreshTTL(UserId userId, String httpSessionId) {
        String channelIdKey = buildChannelIdKey(userId);
        try {
            Session httpSession = httpSessionRepository.findById(httpSessionId);
            if (httpSession != null) {
                httpSession.setLastAccessedTime(Instant.now());
                stringRedisTemplate.expire(channelIdKey, TTL, TimeUnit.SECONDS);
            }
        } catch (Exception ex) {
            log.error("Redis expire failed. key : {}", channelIdKey);
        }
    }

    public String getUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }

    private String buildChannelIdKey(UserId userId) {
        String NAMESPACE = "message:user";
        return "%s:%d:%s".formatted(NAMESPACE, userId.id(), IdKey.CHANNEL_ID.getValue());
    }
}
