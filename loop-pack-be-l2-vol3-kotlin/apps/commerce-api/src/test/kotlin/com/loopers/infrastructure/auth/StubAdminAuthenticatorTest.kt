package com.loopers.infrastructure.auth

import com.loopers.support.auth.AdminPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class StubAdminAuthenticatorTest {
    private fun authenticator(vararg credentials: Pair<String, String>): StubAdminAuthenticator =
        StubAdminAuthenticator(
            AdminAuthProperties(
                stubCredentials = credentials.map { AdminAuthProperties.Credential(id = it.first, password = it.second) },
            ),
        )

    @DisplayName("자격 증명을 검증할 때, ")
    @Nested
    inner class Authenticate {
        @DisplayName("허용 목록에 있는 ID 와 비밀번호면, 해당 ID 의 principal 이 반환된다.")
        @Test
        fun returnsPrincipal_whenCredentialMatches() {
            // arrange
            val sut = authenticator("admin" to "admin1234")

            // act
            val principal = sut.authenticate("admin", "admin1234")

            // assert
            assertThat(principal).isEqualTo(AdminPrincipal("admin"))
        }

        @DisplayName("ID 는 맞고 비밀번호가 틀리면, null 이 반환된다.")
        @Test
        fun returnsNull_whenPasswordDoesNotMatch() {
            // arrange
            val sut = authenticator("admin" to "admin1234")

            // act
            val principal = sut.authenticate("admin", "wrong-password")

            // assert
            assertThat(principal).isNull()
        }

        @DisplayName("허용 목록에 없는 ID 면, null 이 반환된다.")
        @Test
        fun returnsNull_whenIdIsNotRegistered() {
            // arrange
            val sut = authenticator("admin" to "admin1234")

            // act
            val principal = sut.authenticate("stranger", "admin1234")

            // assert
            assertThat(principal).isNull()
        }

        @DisplayName("ID 의 대소문자가 다르면, null 이 반환된다.")
        @Test
        fun returnsNull_whenIdCaseDiffers() {
            // arrange
            val sut = authenticator("admin" to "admin1234")

            // act
            val principal = sut.authenticate("ADMIN", "admin1234")

            // assert
            assertThat(principal).isNull()
        }

        /**
         * 이 테스트가 이 클래스의 존재 이유다.
         * 설정 누락으로 허용 목록이 비었을 때 "검증할 것이 없으니 통과" 로 바뀌면 운영 어드민이 무방비로 열린다.
         * 평소에 아무도 밟지 않는 경로라 다른 테스트로는 잡히지 않는다.
         */
        @DisplayName("허용 목록이 비어 있으면, 어떤 자격 증명이든 null 이 반환된다.")
        @Test
        fun returnsNull_whenCredentialListIsEmpty() {
            // arrange
            val sut = authenticator()

            // act
            val principal = sut.authenticate("admin", "admin1234")

            // assert
            assertThat(principal).isNull()
        }

        @DisplayName("허용 목록에 여러 계정이 있으면, 각각으로 인증할 수 있다.")
        @Test
        fun returnsPrincipal_forEachRegisteredCredential() {
            // arrange
            val sut = authenticator("admin" to "admin1234", "operator" to "operator5678")

            // act
            val first = sut.authenticate("admin", "admin1234")
            val second = sut.authenticate("operator", "operator5678")

            // assert
            assertThat(listOf(first, second)).containsExactly(AdminPrincipal("admin"), AdminPrincipal("operator"))
        }
    }
}
