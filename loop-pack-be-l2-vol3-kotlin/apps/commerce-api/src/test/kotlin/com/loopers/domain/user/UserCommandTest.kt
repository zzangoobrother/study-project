package com.loopers.domain.user

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class UserCommandTest {
    @DisplayName("SignUp 커맨드를 문자열로 변환할 때, ")
    @Nested
    inner class SignUpToString {
        @DisplayName("비밀번호는 마스킹되고, 다른 필드는 그대로 노출된다.")
        @Test
        fun masksPassword_whenConvertedToString() {
            // arrange
            val command = UserCommand.SignUp(
                loginId = LoginId("loopers01"),
                password = RawPassword("Loopers1!"),
                name = UserName("홍길동"),
                birthDate = BirthDate.from("1990-01-01"),
                email = Email("loopers@loopers.com"),
            )

            // act
            val result = command.toString()

            // assert
            assertAll(
                { assertThat(result).doesNotContain("Loopers1!") },
                { assertThat(result).contains("loginId=loopers01") },
                { assertThat(result).contains("name=홍길동") },
                { assertThat(result).contains("birthDate=1990-01-01") },
                { assertThat(result).contains("email=loopers@loopers.com") },
            )
        }
    }
}
