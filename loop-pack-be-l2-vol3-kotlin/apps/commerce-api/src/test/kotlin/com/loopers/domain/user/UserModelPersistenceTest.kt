package com.loopers.domain.user

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

/**
 * 설계 문서(2026-08-05-value-object-design.md) 규칙 9 를 고정하는 테스트.
 *
 * Hibernate 는 FIELD access 에서 @Embeddable 을 no-arg 생성자로 만든 뒤 필드에 직접 주입하므로,
 * 값 객체의 init 검증이 조회 시점에는 실행되지 않는다.
 * 이 동작 덕분에 검증 규칙을 강화해도 과거 데이터 조회가 깨지지 않는다.
 */
@SpringBootTest
class UserModelPersistenceTest {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @DisplayName("현재 검증 규칙을 위반하는 데이터가 이미 저장되어 있을 때, ")
    @Nested
    inner class LoadLegacyRow {
        @DisplayName("값 객체의 init 검증이 실행되지 않아 조회에 성공한다.")
        @Test
        @Transactional
        fun loadsUser_withoutRunningValueObjectValidation() {
            // arrange — 값 객체 생성자를 거치지 않고 규칙 위반 데이터를 직접 넣는다.
            entityManager.createNativeQuery(
                """
                INSERT INTO users (login_id, password, name, birth_date, email, created_at, updated_at)
                VALUES ('!!!', 'broken', '홍 길 동', '1990-01-01', 'not-an-email', NOW(), NOW())
                """.trimIndent(),
            ).executeUpdate()
            entityManager.flush()
            entityManager.clear()

            // act — init 검증이 돌았다면 여기서 CoreException 이 터진다.
            val user = entityManager
                .createQuery("SELECT u FROM UserModel u", UserModel::class.java)
                .singleResult

            // assert
            assertAll(
                { assertThat(user.loginId.value).isEqualTo("!!!") },
                { assertThat(user.name.value).isEqualTo("홍 길 동") },
                { assertThat(user.email.value).isEqualTo("not-an-email") },
            )
        }
    }
}
