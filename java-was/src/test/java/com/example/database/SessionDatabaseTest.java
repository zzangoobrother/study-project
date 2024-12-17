package com.example.database;

import com.example.http.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class SessionDatabaseTest {

    private Long validUserPk;
    private String sessionKey;

    @BeforeEach
    void setUp() {
        validUserPk = 1L;
        SessionDatabase.clear();
    }

    @Test
    void 정상적인_세션_저장() {
        validUserPk = 1L;

        Session session = SessionDatabase.save(validUserPk);

        assertThat(session)
                .isNotNull()
                .extracting(Session::getUserPk, Session::getCreationTime, Session::getLastAccessTime, Session::getTimeout)
                .doesNotContainNull()
                .containsExactly(validUserPk, session.getCreationTime(), session.getLastAccessTime(), 3600L);
    }

    @Test
    void userPk가_null인_경우_예외_발생() {
        Long nullUserPk = null;

        assertThatThrownBy(() -> SessionDatabase.save(nullUserPk))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용자의 PK는 null 일 수 없습니다.");
    }

    @Test
    void 동일한_userPk로_여러_세션_저장() {
        validUserPk = 1L;

        Session session1 = SessionDatabase.save(validUserPk);
        Session session2 = SessionDatabase.save(validUserPk);

        assertThat(session1.getSessionId())
                .isNotEqualTo(session2.getSessionId());
    }

    @Test
    void 정상적인_세션_조회() {
        Session session = SessionDatabase.save(validUserPk);

        Session foundSession = SessionDatabase.find(session.getSessionId());

        assertThat(foundSession)
                .isNotNull()
                .extracting(Session::getUserPk)
                .isEqualTo(validUserPk);
    }

    @Test
    void 존재하지_않는_키로_조회시_null_반환() {
        sessionKey = UUID.randomUUID().toString();

        Session foundSession = SessionDatabase.find(sessionKey);

        assertThat(foundSession).isNull();
    }

    @Test
    void 저장_후_조회() {
        Session session = SessionDatabase.save(validUserPk);

        Session foundSession = SessionDatabase.find(session.getSessionId());

        assertThat(foundSession).isEqualTo(session);
    }

    @Test
    void 정상적인_세션_삭제() {
        Session session = SessionDatabase.save(validUserPk);

        SessionDatabase.delete(session.getSessionId());

        assertThat(SessionDatabase.find(session.getSessionId())).isNull();
    }

    @Test
    void 존재하지_않는_키로_삭제시_예외_발생_없음() {
        sessionKey = UUID.randomUUID().toString();

        assertThatCode(() -> SessionDatabase.delete(sessionKey)).doesNotThrowAnyException();
    }
}
