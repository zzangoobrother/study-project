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
}
