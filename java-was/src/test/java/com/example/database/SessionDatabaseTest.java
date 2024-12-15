package com.example.database;

import com.example.http.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
