package com.loopers.interfaces.api.user

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class UserV1DtoTest {
    @DisplayName("SignUpRequest 를 문자열로 변환할 때, ")
    @Nested
    inner class SignUpRequestToString {
        @DisplayName("비밀번호는 마스킹되고, 다른 필드는 그대로 노출된다.")
        @Test
        fun masksPassword_whenConvertedToString() {
            // arrange
            val request = UserV1Dto.SignUpRequest(
                loginId = "loopers01",
                password = "Loopers1!",
                name = "홍길동",
                birthDate = "1990-01-01",
                email = "loopers@loopers.com",
            )

            // act
            val result = request.toString()

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
