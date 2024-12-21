package com.example.http;

import java.time.LocalDateTime;
import java.util.UUID;

public class Session {

    private final String sessionId;
    private final Long userId;
    private final LocalDateTime creationTime;
    private final long timeout;
    private final LocalDateTime lastAccessTime;

    public Session(Long userId, LocalDateTime nowDateTime, long timeout) {
        this.sessionId = UUID.randomUUID().toString();
        this.userId = validateUserPk(userId);
        this.creationTime = validateNowDateTime(nowDateTime);
        this.timeout = validateTimeout(timeout);
        this.lastAccessTime = this.creationTime;
    }

    public Session(String sessionId, Long userId, LocalDateTime creationTime, long timeout, LocalDateTime lastAccessTime) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.creationTime = creationTime;
        this.timeout = timeout;
        this.lastAccessTime = lastAccessTime;
    }

    private long validateUserPk(Long userPk) {
        if (userPk == null) {
            throw new IllegalArgumentException("사용자의 PK는 null 일 수 없습니다.");
        }

        return userPk;
    }

    private LocalDateTime validateNowDateTime(LocalDateTime nowDateTime) {
        if (nowDateTime == null) {
            throw new IllegalArgumentException("현재 시간은 null 일 수 없습니다.");
        }

        return nowDateTime;
    }

    private long validateTimeout(long timeout) {
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout은 0보다 커야 합니다.");
        }

        return timeout;
    }

    public Session updateLastAccessTime() {
        return new Session(getSessionId(), getUserId(), getCreationTime(), getTimeout(), LocalDateTime.now());
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(getLastAccessTime().plusSeconds(getTimeout()));
    }

    public String getSessionId() {
        return sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDateTime getCreationTime() {
        return creationTime;
    }

    public long getTimeout() {
        return timeout;
    }

    public LocalDateTime getLastAccessTime() {
        return lastAccessTime;
    }
}
