package com.loopers.infrastructure.user

import com.loopers.domain.user.EncodedPassword
import com.loopers.domain.user.RawPassword
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class Sha256PasswordEncoderTest {
    private val passwordEncoder = Sha256PasswordEncoder()

    @DisplayName("비밀번호를 인코딩할 때, ")
    @Nested
    inner class Encode {
        @DisplayName("같은 평문을 두 번 인코딩하면, salt 가 달라 서로 다른 결과가 나온다.")
        @Test
        fun returnsDifferentResults_whenSameRawPasswordIsEncodedTwice() {
            // arrange
            val rawPassword = RawPassword("Loopers1!")

            // act
            val first = passwordEncoder.encode(rawPassword)
            val second = passwordEncoder.encode(rawPassword)

            // assert
            assertAll(
                { assertThat(first).isNotEqualTo(second) },
                { assertThat(first.value).doesNotContain("Loopers1!") },
                { assertThat(second.value).doesNotContain("Loopers1!") },
            )
        }

        @DisplayName("인코딩 결과는 'Base64(salt):Base64(hash)' 형태다.")
        @Test
        fun returnsSaltAndHashJoinedByColon_whenPasswordIsEncoded() {
            // act
            val encoded = passwordEncoder.encode(RawPassword("Loopers1!"))

            // assert
            assertThat(encoded.value.split(":")).hasSize(2)
        }
    }

    @DisplayName("비밀번호를 검증할 때, ")
    @Nested
    inner class Matches {
        @DisplayName("원본 평문을 주면, true 를 반환한다.")
        @Test
        fun returnsTrue_whenRawPasswordIsCorrect() {
            // arrange
            val rawPassword = RawPassword("Loopers1!")
            val encoded = passwordEncoder.encode(rawPassword)

            // act
            val result = passwordEncoder.matches(rawPassword, encoded)

            // assert
            assertThat(result).isTrue()
        }

        @DisplayName("다른 평문을 주면, false 를 반환한다.")
        @Test
        fun returnsFalse_whenRawPasswordIsWrong() {
            // arrange
            val encoded = passwordEncoder.encode(RawPassword("Loopers1!"))

            // act
            val result = passwordEncoder.matches(RawPassword("Loopers2@"), encoded)

            // assert
            assertThat(result).isFalse()
        }

        @DisplayName("형식이 깨진 인코딩 값을 주면, 예외 대신 false 를 반환한다.")
        @Test
        fun returnsFalse_whenEncodedPasswordIsMalformed() {
            // arrange
            val rawPassword = RawPassword("Loopers1!")

            // act & assert
            assertAll(
                { assertThat(passwordEncoder.matches(rawPassword, EncodedPassword("broken-value"))).isFalse() },
                { assertThat(passwordEncoder.matches(rawPassword, EncodedPassword(""))).isFalse() },
                { assertThat(passwordEncoder.matches(rawPassword, EncodedPassword("!!!:???"))).isFalse() },
            )
        }
    }
}
