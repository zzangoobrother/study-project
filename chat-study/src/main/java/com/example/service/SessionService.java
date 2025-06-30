package com.example.service;

import com.example.constants.IdKey;
import com.example.constants.KeyPrefix;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class SessionService {
    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SessionRepository<? extends Session> httpSessionRepository;
    private final CacheService cacheService;
    private final long TTL = 300;

    public SessionService(SessionRepository<? extends Session> httpSessionRepository, CacheService cacheService) {
        this.httpSessionRepository = httpSessionRepository;
        this.cacheService = cacheService;
    }

    public List<UserId> getOnlineParticipantUSerIds(ChannelId channelId, List<UserId> userIds) {
        List<String> channelIdKeys = userIds.stream().map(this::buildChannelIdKey).toList();
        List<String> channelIds = cacheService.get(channelIdKeys);
        if (channelIds != null) {
            List<UserId> onlineParticipantUSerIds = new ArrayList<>(channelIds.size());
            String chId = channelId.toString();
            for (int idx = 0; idx < userIds.size(); idx++) {
                String value = channelIds.get(idx);
                onlineParticipantUSerIds.add(value != null && value.equals(chId) ? userIds.get(idx) : null);
            }

            return onlineParticipantUSerIds;
        }

        return Collections.emptyList();
    }

    public boolean setActiveChannel(UserId userId, ChannelId channelId) {
        return cacheService.set(buildChannelIdKey(userId), channelId.id().toString(), TTL);
    }

    public boolean removeActiveChannel(UserId userId) {
        return cacheService.delete(buildChannelIdKey(userId));
    }

    public void refreshTTL(UserId userId, String httpSessionId) {
        String channelIdKey = buildChannelIdKey(userId);
        try {
            Session httpSession = httpSessionRepository.findById(httpSessionId);
            if (httpSession != null) {
                httpSession.setLastAccessedTime(Instant.now());
                cacheService.expire(channelIdKey, TTL);
            }
        } catch (Exception ex) {
            log.error("Redis find failed. httpSessionId : {}, cause : {}", httpSessionId, ex.getMessage());
        }
    }

    public String getUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }

    private String buildChannelIdKey(UserId userId) {
        return cacheService.buildKey(KeyPrefix.USER, userId.id().toString(), IdKey.CHANNEL_ID.getValue());
    }
}
