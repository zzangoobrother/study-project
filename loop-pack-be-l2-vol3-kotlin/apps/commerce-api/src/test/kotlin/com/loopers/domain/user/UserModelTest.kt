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

    /** 해싱 동작이 아닌 조립·교차 검증 로직만 테스트하기 위한 가짜 인코더. */
    private class FakePasswordEncoder : PasswordEncoder {
        override fun encode(rawPassword: RawPassword): EncodedPassword =
            EncodedPassword("encoded:${rawPassword.value}")

        override fun matches(rawPassword: RawPassword, encodedPassword: EncodedPassword): Boolean =
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
        loginId = LoginId(loginId),
        rawPassword = RawPassword(rawPassword),
        name = UserName(name),
        birthDate = BirthDate.from(birthDate),
        email = Email(email),
        passwordEncoder = passwordEncoder,
    )

    @DisplayName("유저를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("모든 값 객체가 유효하면, 정상 생성되고 비밀번호는 인코딩되어 저장된다.")
        @Test
        fun createsUser_whenAllValueObjectsAreValid() {
            // act
            val user = createUser()

            // assert
            assertAll(
                { assertThat(user.loginId).isEqualTo(LoginId("loopers01")) },
                { assertThat(user.name).isEqualTo(UserName("홍길동")) },
                { assertThat(user.birthDate).isEqualTo(BirthDate(LocalDate.of(1990, 1, 1))) },
                { assertThat(user.email).isEqualTo(Email("loopers@loopers.com")) },
                { assertThat(user.password).isEqualTo(EncodedPassword("encoded:Loopers1!")) },
                { assertThat(user.password).isNotEqualTo(EncodedPassword("Loopers1!")) },
            )
        }
    }

    @DisplayName("비밀번호와 생년월일의 교차 규칙을 검증할 때, ")
    @Nested
    inner class ValidatePasswordAgainstBirthDate {
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
            assertThat(user.password).isEqualTo(EncodedPassword("encoded:$rawPassword"))
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    inner class ChangePassword {
        @DisplayName("기존 비밀번호가 일치하고 새 비밀번호가 유효하면, 인코딩되어 교체된다.")
        @Test
        fun changesPassword_whenCurrentPasswordMatchesAndNewPasswordIsValid() {
            // arrange
            val user = createUser(rawPassword = "Loopers1!")

            // act
            user.changePassword(
                currentPassword = RawPassword("Loopers1!"),
                newPassword = RawPassword("Loopers2@"),
                passwordEncoder = passwordEncoder,
            )

            // assert
            assertAll(
                { assertThat(user.password).isEqualTo(EncodedPassword("encoded:Loopers2@")) },
                { assertThat(user.password).isNotEqualTo(EncodedPassword("Loopers2@")) },
            )
        }

        @DisplayName("기존 비밀번호가 일치하지 않으면, UNAUTHORIZED 예외가 발생하고 비밀번호가 바뀌지 않는다.")
        @Test
        fun throwsUnauthorizedException_whenCurrentPasswordDoesNotMatch() {
            // arrange
            val user = createUser(rawPassword = "Loopers1!")

            // act
            val result = assertThrows<CoreException> {
                user.changePassword(
                    currentPassword = RawPassword("Wrong123!"),
                    newPassword = RawPassword("Loopers2@"),
                    passwordEncoder = passwordEncoder,
                )
            }

            // assert
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED) },
                { assertThat(user.password).isEqualTo(EncodedPassword("encoded:Loopers1!")) },
            )
        }

        @DisplayName("새 비밀번호가 기존 비밀번호와 같으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenNewPasswordIsSameAsCurrent() {
            // arrange
            val user = createUser(rawPassword = "Loopers1!")

            // act
            val result = assertThrows<CoreException> {
                user.changePassword(
                    currentPassword = RawPassword("Loopers1!"),
                    newPassword = RawPassword("Loopers1!"),
                    passwordEncoder = passwordEncoder,
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["Abc19900101!", "pass900101@x"])
        fun throwsBadRequestException_whenNewPasswordContainsBirthDate(newPassword: String) {
            // arrange
            val user = createUser(rawPassword = "Loopers1!", birthDate = "1990-01-01")

            // act
            val result = assertThrows<CoreException> {
                user.changePassword(
                    currentPassword = RawPassword("Loopers1!"),
                    newPassword = RawPassword(newPassword),
                    passwordEncoder = passwordEncoder,
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("기존 비밀번호가 틀리고 새 비밀번호도 정책을 위반하면, 인증 실패인 UNAUTHORIZED 가 우선한다.")
        @Test
        fun throwsUnauthorizedException_whenCurrentPasswordIsWrongAndNewPasswordViolatesPolicy() {
            // arrange
            val user = createUser(rawPassword = "Loopers1!", birthDate = "1990-01-01")

            // act
            val result = assertThrows<CoreException> {
                user.changePassword(
                    currentPassword = RawPassword("Wrong123!"),
                    newPassword = RawPassword("Abc19900101!"),
                    passwordEncoder = passwordEncoder,
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }
    }
}
