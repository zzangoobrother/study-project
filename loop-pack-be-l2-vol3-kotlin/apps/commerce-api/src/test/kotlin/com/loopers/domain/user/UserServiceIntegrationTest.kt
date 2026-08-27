package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.time.LocalDate

@SpringBootTest
class UserServiceIntegrationTest @Autowired constructor(
    private val userService: UserService,
    private val databaseCleanUp: DatabaseCleanUp,
    private val passwordEncoder: PasswordEncoder,
) {
    @MockitoSpyBean
    private lateinit var userRepository: UserRepository

    private fun signUpCommand(
        loginId: String = "loopers01",
        password: String = "Loopers1!",
        name: String = "홍길동",
        birthDate: String = "1990-01-01",
        email: String = "loopers@loopers.com",
    ) = UserCommand.SignUp(
        loginId = LoginId(loginId),
        password = RawPassword(password),
        name = UserName(name),
        birthDate = BirthDate.from(birthDate),
        email = Email(email),
    )

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("회원 가입을 할 때, ")
    @Nested
    inner class SignUp {
        @DisplayName("유효한 정보를 주면, User 저장이 수행된다.")
        @Test
        fun savesUser_whenValidCommandIsProvided() {
            // arrange
            val command = signUpCommand()

            // act
            val user = userService.signUp(command)

            // assert
            verify(userRepository).save(any())
            assertAll(
                { assertThat(user.id).isPositive() },
                { assertThat(user.loginId).isEqualTo(LoginId("loopers01")) },
                { assertThat(user.name).isEqualTo(UserName("홍길동")) },
                { assertThat(user.birthDate).isEqualTo(BirthDate(LocalDate.of(1990, 1, 1))) },
                { assertThat(user.email).isEqualTo(Email("loopers@loopers.com")) },
                { assertThat(user.password.value).doesNotContain("Loopers1!") },
                { assertThat(passwordEncoder.matches(RawPassword("Loopers1!"), user.password)).isTrue() },
                { assertThat(passwordEncoder.matches(RawPassword("Loopers2@"), user.password)).isFalse() },
            )
        }

        @DisplayName("이미 가입된 로그인 ID 로 시도하면, CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflictException_whenLoginIdIsAlreadyRegistered() {
            // arrange
            userService.signUp(signUpCommand())

            // act
            val result = assertThrows<CoreException> {
                userService.signUp(signUpCommand(email = "another@loopers.com"))
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }

    @DisplayName("로그인 ID 로 회원을 조회할 때, ")
    @Nested
    inner class GetUser {
        @DisplayName("해당 로그인 ID 의 회원이 존재하면, 회원 정보가 반환된다.")
        @Test
        fun returnsUser_whenLoginIdIsRegistered() {
            // arrange
            userService.signUp(signUpCommand())

            // act
            val user = userService.getUser(LoginId("loopers01"))

            // assert
            assertAll(
                { assertThat(user).isNotNull() },
                { assertThat(user?.loginId).isEqualTo(LoginId("loopers01")) },
                { assertThat(user?.name).isEqualTo(UserName("홍길동")) },
                { assertThat(user?.birthDate).isEqualTo(BirthDate(LocalDate.of(1990, 1, 1))) },
                { assertThat(user?.email).isEqualTo(Email("loopers@loopers.com")) },
            )
        }

        @DisplayName("해당 로그인 ID 의 회원이 존재하지 않으면, null 이 반환된다.")
        @Test
        fun returnsNull_whenLoginIdIsNotRegistered() {
            // act
            val user = userService.getUser(LoginId("nobody"))

            // assert
            assertThat(user).isNull()
        }

        @DisplayName("소프트 삭제된 회원이면, null 이 반환된다.")
        @Test
        fun returnsNull_whenUserIsSoftDeleted() {
            // arrange
            val saved = userService.signUp(signUpCommand())
            saved.delete()
            userRepository.save(saved)

            // act
            val user = userService.getUser(LoginId("loopers01"))

            // assert
            assertThat(user).isNull()
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    inner class ChangePassword {
        private fun changePasswordCommand(
            loginId: String = "loopers01",
            currentPassword: String = "Loopers1!",
            newPassword: String = "Loopers2@",
        ) = UserCommand.ChangePassword(
            loginId = LoginId(loginId),
            currentPassword = RawPassword(currentPassword),
            newPassword = RawPassword(newPassword),
        )

        @DisplayName("기존 비밀번호가 일치하면, 저장된 비밀번호가 새 값으로 교체된다.")
        @Test
        fun changesStoredPassword_whenCurrentPasswordMatches() {
            // arrange
            userService.signUp(signUpCommand())

            // act
            userService.changePassword(changePasswordCommand())

            // assert
            val user = userService.getUser(LoginId("loopers01"))
            assertAll(
                { assertThat(user).isNotNull() },
                { assertThat(passwordEncoder.matches(RawPassword("Loopers2@"), user!!.password)).isTrue() },
                { assertThat(passwordEncoder.matches(RawPassword("Loopers1!"), user!!.password)).isFalse() },
                { assertThat(user!!.password.value).doesNotContain("Loopers2@") },
            )
        }

        @DisplayName("가입되지 않은 로그인 ID 면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorizedException_whenLoginIdIsNotRegistered() {
            // act
            val result = assertThrows<CoreException> {
                userService.changePassword(changePasswordCommand(loginId = "nobody"))
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("소프트 삭제된 회원이면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorizedException_whenUserIsSoftDeleted() {
            // arrange
            val saved = userService.signUp(signUpCommand())
            saved.delete()
            userRepository.save(saved)

            // act
            val result = assertThrows<CoreException> {
                userService.changePassword(changePasswordCommand())
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("기존 비밀번호가 일치하지 않으면, UNAUTHORIZED 예외가 발생하고 저장된 비밀번호가 바뀌지 않는다.")
        @Test
        fun throwsUnauthorizedException_whenCurrentPasswordDoesNotMatch() {
            // arrange
            userService.signUp(signUpCommand())

            // act
            val result = assertThrows<CoreException> {
                userService.changePassword(changePasswordCommand(currentPassword = "Wrong123!"))
            }

            // assert
            val user = userService.getUser(LoginId("loopers01"))
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED) },
                { assertThat(passwordEncoder.matches(RawPassword("Loopers1!"), user!!.password)).isTrue() },
            )
        }

        @DisplayName("새 비밀번호가 기존 비밀번호와 같으면, salt 가 매번 달라도 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenNewPasswordIsSameAsCurrent() {
            // arrange
            userService.signUp(signUpCommand())

            // act
            val result = assertThrows<CoreException> {
                userService.changePassword(changePasswordCommand(newPassword = "Loopers1!"))
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("가입되지 않은 로그인 ID 와 기존 비밀번호 불일치의 예외 메시지가 동일하다.")
        @Test
        fun returnsIdenticalMessage_forUnknownLoginIdAndWrongPassword() {
            // arrange
            userService.signUp(signUpCommand())

            // act
            val unknownLoginId = assertThrows<CoreException> {
                userService.changePassword(changePasswordCommand(loginId = "nobody"))
            }
            val wrongPassword = assertThrows<CoreException> {
                userService.changePassword(changePasswordCommand(currentPassword = "Wrong123!"))
            }

            // assert
            assertThat(unknownLoginId.customMessage).isEqualTo(wrongPassword.customMessage)
        }
    }

    @DisplayName("삭제 포함으로 회원을 여러 건 조회할 때, ")
    @Nested
    inner class GetUsersIncludingDeleted {
        @DisplayName("여러 ID 를 IN 절 한 번으로 조회한다.")
        @Test
        fun returnsMultipleUsers() {
            // arrange
            val first = userService.signUp(signUpCommand(loginId = "loopers01", email = "loopers01@loopers.com"))
            val second = userService.signUp(signUpCommand(loginId = "loopers02", email = "loopers02@loopers.com"))

            // act
            val found = userService.getUsersIncludingDeleted(listOf(first.id, second.id))

            // assert
            assertThat(found.map { it.id }).containsExactlyInAnyOrder(first.id, second.id)
        }

        /**
         * getUser(loginId) 는 소프트 삭제된 회원을 제외하지만, 이 메서드는 정반대다.
         * 어드민 주문 목록에서 탈퇴 회원을 결과에서 빼면 "탈퇴한 회원의 주문" 과 "알 수 없는 회원의 주문" 이
         * 둘 다 user = null 로 뭉개지므로, 이 메서드의 존재 이유가 곧 이 테스트다.
         */
        @DisplayName("소프트 삭제된 회원도 결과에 포함된다.")
        @Test
        fun includesSoftDeletedUsers() {
            // arrange
            val saved = userService.signUp(signUpCommand())
            saved.delete()
            userRepository.save(saved)

            // act
            val found = userService.getUsersIncludingDeleted(listOf(saved.id))

            // assert
            assertAll(
                { assertThat(found.map { it.id }).containsExactly(saved.id) },
                { assertThat(found.first().deletedAt).isNotNull() },
            )
        }

        @DisplayName("ID 목록이 비어 있으면, 빈 목록이 반환된다.")
        @Test
        fun returnsEmptyList_whenIdsAreEmpty() {
            // act
            val found = userService.getUsersIncludingDeleted(emptyList())

            // assert
            assertThat(found).isEmpty()
        }

        @DisplayName("존재하지 않는 ID 가 섞여 있으면, 그 ID 만 결과에서 빠진다.")
        @Test
        fun excludesOnlyNonExistentIds() {
            // arrange
            val saved = userService.signUp(signUpCommand())

            // act
            val found = userService.getUsersIncludingDeleted(listOf(saved.id, 99999L))

            // assert
            assertThat(found.map { it.id }).containsExactly(saved.id)
        }
    }
}
