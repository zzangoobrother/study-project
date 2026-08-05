package com.loopers.domain.user

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class EncodedPasswordTest {
    @DisplayName("인코딩된 비밀번호를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("어떤 문자열이든 검증 없이 생성된다.")
        @Test
        fun createsEncodedPassword_withoutValidation() {
            // act & assert
            assertAll(
                { assertThat(EncodedPassword("c2FsdA==:aGFzaA==").value).isEqualTo("c2FsdA==:aGFzaA==") },
                { assertThat(EncodedPassword("").value).isEmpty() },
                { assertThat(EncodedPassword("broken-value").value).isEqualTo("broken-value") },
            )
        }
    }

    @DisplayName("인코딩된 비밀번호는 자격 증명 산출물이므로, ")
    @Nested
    inner class Masking {
        @DisplayName("toString 에 해시가 노출되지 않는다.")
        @Test
        fun doesNotExposeHashInToString() {
            // arrange
            val encodedPassword = EncodedPassword("c2FsdA==:aGFzaA==")

            // act
            val result = encodedPassword.toString()

            // assert
            assertAll(
                { assertThat(result).doesNotContain("aGFzaA==") },
                { assertThat(result).isEqualTo("****") },
            )
        }
    }

    @DisplayName("인코딩된 비밀번호는 값 객체이므로, ")
    @Nested
    inner class ValueSemantics {
        @DisplayName("같은 값이면 동등하다.")
        @Test
        fun equalsByValue() {
            // arrange
            val first = EncodedPassword("c2FsdA==:aGFzaA==")
            val second = EncodedPassword("c2FsdA==:aGFzaA==")

            // assert
            assertAll(
                { assertThat(first).isEqualTo(second) },
                { assertThat(first.hashCode()).isEqualTo(second.hashCode()) },
            )
        }
    }
}
