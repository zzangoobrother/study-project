package com.loopers.interfaces.api.user

import com.loopers.application.user.UserInfo
import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.UserName
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

    @DisplayName("UserInfo 를 MeResponse 로 변환할 때, ")
    @Nested
    inner class MeResponseFrom {
        @DisplayName("이름의 마지막 글자를 마스킹하고, 나머지 필드는 그대로 옮긴다.")
        @Test
        fun masksName_whenConvertedFromUserInfo() {
            // arrange
            val info = UserInfo(
                id = 1L,
                loginId = LoginId("loopers01"),
                name = UserName("홍길동"),
                birthDate = BirthDate.from("1990-01-01"),
                email = Email("loopers@loopers.com"),
            )

            // act
            val response = UserV1Dto.MeResponse.from(info)

            // assert
            assertAll(
                { assertThat(response.loginId).isEqualTo("loopers01") },
                { assertThat(response.name).isEqualTo("홍길*") },
                { assertThat(response.birthDate).isEqualTo("1990-01-01") },
                { assertThat(response.email).isEqualTo("loopers@loopers.com") },
            )
        }
    }
}
