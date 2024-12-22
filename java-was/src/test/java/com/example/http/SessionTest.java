package com.example.http;

import com.example.webserver.http.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionTest {

    private Long validUserPk;
    private LocalDateTime nowDateTime;
    private long validTimeout;

    @BeforeEach
    void setUp() {
        validUserPk = 1L;
        nowDateTime = LocalDateTime.of(2024, 12, 15, 0, 0, 0);
        validTimeout = 1800L;
    }

    @Test
    void Session_생성시_유효한_값으로_생성되는지_테스트() {
        Session session = new Session(validUserPk, nowDateTime, validTimeout);

        assertThat(session)
                .isNotNull()
                .extracting(Session::getSessionId, Session::getUserId, Session::getCreationTime, Session::getLastAccessTime, Session::getTimeout)
                .doesNotContainNull()
                .containsExactly(session.getSessionId(), validUserPk, nowDateTime, nowDateTime, validTimeout);
    }

    @Test
    void Session_생성시_userPk가_null이면_예외_발생() {
        assertThatThrownBy(() -> new Session(null, nowDateTime, validTimeout))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용자의 PK는 null 일 수 없습니다.");
    }

    @Test
    void Session_생성시_nowDateTime이_null이면_예외_발생() {
        assertThatThrownBy(() -> new Session(validUserPk, null, validTimeout))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("현재 시간은 null 일 수 없습니다.");
    }

    @Test
    void Session_생성시_timeout이_0보다_작으면_예외_발생() {
        assertThatThrownBy(() -> new Session(validUserPk, nowDateTime, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timeout은 0보다 커야 합니다.");
    }

    @Test
    void updateLastAccessTime_메서드가_lastAccessTime을_현재_시간으로_업데이트하는지_테스트() {
        Session session = new Session(validUserPk, nowDateTime, validTimeout);
        LocalDateTime beforeUpdate = session.getLastAccessTime();

        Session updateSession = session.updateLastAccessTime();
        LocalDateTime afterUpdate = updateSession.getLastAccessTime();

        assertThat(afterUpdate).isAfter(beforeUpdate);
    }

    @Test
    void isExpired_메서드가_세션의_만료_여부를_올바르게_반환하는지_테스트() {
        Session session = new Session(validUserPk, nowDateTime.minusSeconds(2), 1);

        assertThat(session.isExpired()).isTrue();
    }
}
