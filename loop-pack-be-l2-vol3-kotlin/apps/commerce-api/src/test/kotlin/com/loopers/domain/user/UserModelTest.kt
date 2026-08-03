package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate

class UserModelTest {
    private val passwordEncoder = FakePasswordEncoder()

    /** 해싱 동작이 아닌 검증 로직만 테스트하기 위한 가짜 인코더. */
    private class FakePasswordEncoder : PasswordEncoder {
        override fun encode(rawPassword: String): String = "encoded:$rawPassword"

        override fun matches(rawPassword: String, encodedPassword: String): Boolean =
            encodedPassword == encode(rawPassword)
    }

    /** 검증 대상 필드만 바꿔 가며 테스트하기 위한 헬퍼. */
    private fun createUser(
        loginId: String = "loopers01",
        rawPassword: String = "Loopers1!",
        name: String = "홍길동",
        birthDate: String = "1990-01-01",
        email: String = "loopers@loopers.com",
    ): UserModel = UserModel.create(
        loginId = loginId,
        rawPassword = rawPassword,
        name = name,
        birthDate = birthDate,
        email = email,
        passwordEncoder = passwordEncoder,
    )

    @DisplayName("유저를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("모든 정보가 유효하면, 정상 생성되고 비밀번호는 인코딩되어 저장된다.")
        @Test
        fun createsUser_whenAllFieldsAreValid() {
            // act
            val user = createUser()

            // assert
            assertAll(
                { assertThat(user.loginId).isEqualTo("loopers01") },
                { assertThat(user.name).isEqualTo("홍길동") },
                { assertThat(user.birthDate).isEqualTo(LocalDate.of(1990, 1, 1)) },
                { assertThat(user.email).isEqualTo("loopers@loopers.com") },
                { assertThat(user.password).isNotEqualTo("Loopers1!") },
                { assertThat(user.password).isEqualTo("encoded:Loopers1!") },
            )
        }

        @DisplayName("로그인 ID 가 '영문 및 숫자 10자 이내' 형식에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", "loopers_01", "loopers 01", "루퍼스01", "abcdefghijk"])
        fun throwsBadRequestException_whenLoginIdIsInvalid(loginId: String) {
            // act
            val result = assertThrows<CoreException> { createUser(loginId = loginId) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이름이 '한글 또는 영문 20자 이내' 형식에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", "홍 길동", "홍길동2", "홍길동!", "가나다라마바사아자차카타파하가나다라마바사"])
        fun throwsBadRequestException_whenNameIsInvalid(name: String) {
            // act
            val result = assertThrows<CoreException> { createUser(name = name) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이메일이 'xx@yy.zz' 형식에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", "loopers", "loopers@", "@loopers.com", "loopers@loopers", "loopers@loopers."])
        fun throwsBadRequestException_whenEmailIsInvalid(email: String) {
            // act
            val result = assertThrows<CoreException> { createUser(email = email) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이메일 형식은 유효하지만 254자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenEmailExceedsMaxLength() {
            // arrange
            val overLengthEmail = "a".repeat(250) + "@b.com"

            // act
            val result = assertThrows<CoreException> { createUser(email = overLengthEmail) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("생년월일이 'yyyy-MM-dd' 형식이 아니거나 실재하지 않는 날짜면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", "1990/01/01", "19900101", "1990-1-1", "1990-13-01", "1990-02-30"])
        fun throwsBadRequestException_whenBirthDateIsInvalid(birthDate: String) {
            // act
            val result = assertThrows<CoreException> { createUser(birthDate = birthDate) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("생년월일이 미래면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenBirthDateIsInFuture() {
            // arrange
            val tomorrow = LocalDate.now().plusDays(1).toString()

            // act
            val result = assertThrows<CoreException> { createUser(birthDate = tomorrow) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("생년월일이 오늘이면, 정상 생성된다.")
        @Test
        fun createsUser_whenBirthDateIsToday() {
            // arrange
            val today = LocalDate.now()

            // act
            val user = createUser(birthDate = today.toString(), rawPassword = "Loopers1!")

            // assert
            assertThat(user.birthDate).isEqualTo(today)
        }
    }

    @DisplayName("비밀번호 규칙을 검증할 때, ")
    @Nested
    inner class ValidatePassword {
        @DisplayName("8~16자 범위를 벗어나면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["Ab1!", "Abc123!", "Abcdefghij12345!@"])
        fun throwsBadRequestException_whenLengthIsOutOfRange(rawPassword: String) {
            // act
            val result = assertThrows<CoreException> { createUser(rawPassword = rawPassword) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("영문·숫자·특수문자 중 하나라도 빠지면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["abcdefgh", "Password1", "Abcdefg!", "12345678!", "!@#\$%^&*"])
        fun throwsBadRequestException_whenAnyCharacterTypeIsMissing(rawPassword: String) {
            // act
            val result = assertThrows<CoreException> { createUser(rawPassword = rawPassword) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("허용되지 않은 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["비밀번호1234!", "Pass word1!", "Loopers1!\t"])
        fun throwsBadRequestException_whenDisallowedCharacterIsIncluded(rawPassword: String) {
            // act
            val result = assertThrows<CoreException> { createUser(rawPassword = rawPassword) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("생년월일의 yyyyMMdd 표기가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenPasswordContainsBirthDateInFullFormat() {
            // act
            val result = assertThrows<CoreException> {
                createUser(rawPassword = "Abc19900101!", birthDate = "1990-01-01")
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("생년월일의 yyMMdd 표기가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenPasswordContainsBirthDateInShortFormat() {
            // act
            val result = assertThrows<CoreException> {
                createUser(rawPassword = "pass900101@x", birthDate = "1990-01-01")
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("연도 또는 월일이 단독으로 등장하는 것은 차단하지 않는다.")
        @ParameterizedTest
        @ValueSource(strings = ["Secure1990!", "MyPass0101#"])
        fun createsUser_whenPasswordContainsOnlyYearOrMonthDay(rawPassword: String) {
            // act
            val user = createUser(rawPassword = rawPassword, birthDate = "1990-01-01")

            // assert
            assertThat(user.password).isEqualTo("encoded:$rawPassword")
        }
    }
}
