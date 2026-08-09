package com.loopers.interfaces.api

import com.loopers.interfaces.api.user.UserV1Controller
import com.loopers.interfaces.api.user.UserV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT_SIGN_UP = "/api/v1/users"
        private const val ENDPOINT_ME = "/api/v1/users/me"
        private const val ENDPOINT_PASSWORD = "/api/v1/users/me/password"
    }

    private fun signUpRequest(
        loginId: String = "loopers01",
        password: String = "Loopers1!",
        name: String = "홍길동",
        birthDate: String = "1990-01-01",
        email: String = "loopers@loopers.com",
    ) = UserV1Dto.SignUpRequest(
        loginId = loginId,
        password = password,
        name = name,
        birthDate = birthDate,
        email = email,
    )

    private fun jsonEntity(request: UserV1Dto.SignUpRequest): HttpEntity<UserV1Dto.SignUpRequest> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return HttpEntity(request, headers)
    }

    /** loginId 가 null 이면 X-Loopers-LoginId 헤더를 아예 넣지 않는다. */
    private fun headerEntity(loginId: String? = null): HttpEntity<Void> {
        val headers = HttpHeaders().apply {
            loginId?.let { set(UserV1Controller.HEADER_LOGIN_ID, it) }
        }
        return HttpEntity(headers)
    }

    private fun signUp(request: UserV1Dto.SignUpRequest = signUpRequest()) {
        val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
        testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, jsonEntity(request), responseType)
    }

    private fun changePasswordRequest(
        currentPassword: String = "Loopers1!",
        newPassword: String = "Loopers2@",
    ) = UserV1Dto.ChangePasswordRequest(
        currentPassword = currentPassword,
        newPassword = newPassword,
    )

    /** loginId 가 null 이면 X-Loopers-LoginId 헤더를 아예 넣지 않는다. */
    private fun changePasswordEntity(
        request: UserV1Dto.ChangePasswordRequest = changePasswordRequest(),
        loginId: String? = null,
    ): HttpEntity<UserV1Dto.ChangePasswordRequest> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            loginId?.let { set(UserV1Controller.HEADER_LOGIN_ID, it) }
        }
        return HttpEntity(request, headers)
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    inner class SignUp {
        @DisplayName("유효한 정보로 가입하면, 생성된 유저 정보를 반환한다.")
        @Test
        fun returnsUserInfo_whenValidRequestIsProvided() {
            // arrange
            val request = signUpRequest()
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_SIGN_UP,
                HttpMethod.POST,
                jsonEntity(request),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.id).isNotNull() },
                { assertThat(response.body?.data?.loginId).isEqualTo("loopers01") },
                { assertThat(response.body?.data?.name).isEqualTo("홍길동") },
                { assertThat(response.body?.data?.birthDate).isEqualTo("1990-01-01") },
                { assertThat(response.body?.data?.email).isEqualTo("loopers@loopers.com") },
            )
        }

        @DisplayName("가입에 성공해도, 응답 본문에 비밀번호가 노출되지 않는다.")
        @Test
        fun doesNotExposePassword_whenSignUpSucceeds() {
            // arrange
            val request = signUpRequest()

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_SIGN_UP,
                HttpMethod.POST,
                jsonEntity(request),
                String::class.java,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body).doesNotContain("Loopers1!") },
                { assertThat(response.body).doesNotContain("password") },
            )
        }

        @DisplayName("형식에 맞지 않는 이메일로 가입하면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenEmailIsInvalid() {
            // arrange
            val request = signUpRequest(email = "invalid-email")
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_SIGN_UP,
                HttpMethod.POST,
                jsonEntity(request),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("비밀번호 규칙에 맞지 않으면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenPasswordViolatesPolicy() {
            // arrange
            val request = signUpRequest(password = "Abc19900101!", birthDate = "1990-01-01")
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_SIGN_UP,
                HttpMethod.POST,
                jsonEntity(request),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("이미 가입된 로그인 ID 로 가입하면, 409 CONFLICT 를 반환한다.")
        @Test
        fun returnsConflict_whenLoginIdIsAlreadyRegistered() {
            // arrange
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, jsonEntity(signUpRequest()), responseType)

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_SIGN_UP,
                HttpMethod.POST,
                jsonEntity(signUpRequest(email = "another@loopers.com")),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    inner class GetMyInfo {
        @DisplayName("가입된 로그인 ID 로 조회하면, 이름의 마지막 글자가 마스킹된 정보를 반환한다.")
        @Test
        fun returnsMaskedUserInfo_whenLoginIdIsRegistered() {
            // arrange
            signUp()
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.MeResponse>>() {}

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.GET,
                headerEntity("loopers01"),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.loginId).isEqualTo("loopers01") },
                { assertThat(response.body?.data?.name).isEqualTo("홍길*") },
                { assertThat(response.body?.data?.birthDate).isEqualTo("1990-01-01") },
                { assertThat(response.body?.data?.email).isEqualTo("loopers@loopers.com") },
                { assertThat(response.headers.getFirst("Cache-Control")).isEqualTo("no-store") },
                { assertThat(response.headers.getFirst("Vary")).isEqualTo("X-Loopers-LoginId") },
            )
        }

        @DisplayName("조회에 성공해도, 응답 본문에 id·비밀번호·마스킹되지 않은 이름이 노출되지 않는다.")
        @Test
        fun doesNotExposeIdOrPasswordOrRawName_whenLookUpSucceeds() {
            // arrange
            signUp()

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.GET,
                headerEntity("loopers01"),
                String::class.java,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body).doesNotContain("\"id\"") },
                { assertThat(response.body).doesNotContain("Loopers1!") },
                { assertThat(response.body).doesNotContain("password") },
                { assertThat(response.body).doesNotContain("홍길동") },
            )
        }

        @DisplayName("형식에 맞지 않는 로그인 ID 로 조회하면, 400 BAD_REQUEST 를 반환한다.")
        @ParameterizedTest
        @ValueSource(strings = ["loopers-01", "loopers01234"])
        fun returnsBadRequest_whenLoginIdIsInvalid(loginId: String) {
            // arrange
            signUp()
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.MeResponse>>() {}

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.GET,
                headerEntity(loginId),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("가입되지 않은 로그인 ID 로 조회하면, 404 NOT_FOUND 를 반환한다.")
        @Test
        fun returnsNotFound_whenLoginIdIsNotRegistered() {
            // arrange
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.MeResponse>>() {}

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.GET,
                headerEntity("nobody"),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("X-Loopers-LoginId 헤더가 없으면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenHeaderIsMissing() {
            // arrange
            signUp()
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.MeResponse>>() {}

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.GET,
                headerEntity(),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }
    }

    @DisplayName("PUT /api/v1/users/me/password")
    @Nested
    inner class ChangePassword {
        @DisplayName("기존 비밀번호가 일치하면, 200 OK 와 빈 data 를 반환하고 새 비밀번호로 다시 변경할 수 있다.")
        @Test
        fun changesPassword_whenCurrentPasswordMatches() {
            // arrange
            signUp()
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_PASSWORD,
                HttpMethod.PUT,
                changePasswordEntity(loginId = "loopers01"),
                responseType,
            )

            // 실제로 교체되었다면 새 비밀번호가 기존 비밀번호로 동작해야 한다.
            val second = testRestTemplate.exchange(
                ENDPOINT_PASSWORD,
                HttpMethod.PUT,
                changePasswordEntity(
                    request = changePasswordRequest(currentPassword = "Loopers2@", newPassword = "Loopers3#"),
                    loginId = "loopers01",
                ),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data).isNull() },
                { assertThat(second.statusCode).isEqualTo(HttpStatus.OK) },
            )
        }

        @DisplayName("가입되지 않은 로그인 ID 와 기존 비밀번호 불일치가, 완전히 동일한 401 응답을 반환한다.")
        @Test
        fun returnsIdenticalUnauthorized_forUnknownLoginIdAndWrongPassword() {
            // arrange
            signUp()
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

            // act
            val wrongPassword = testRestTemplate.exchange(
                ENDPOINT_PASSWORD,
                HttpMethod.PUT,
                changePasswordEntity(
                    request = changePasswordRequest(currentPassword = "Wrong123!"),
                    loginId = "loopers01",
                ),
                responseType,
            )
            val unknownLoginId = testRestTemplate.exchange(
                ENDPOINT_PASSWORD,
                HttpMethod.PUT,
                changePasswordEntity(loginId = "nobody"),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(wrongPassword.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(unknownLoginId.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(wrongPassword.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
                { assertThat(unknownLoginId.body?.meta?.errorCode).isEqualTo(wrongPassword.body?.meta?.errorCode) },
                { assertThat(unknownLoginId.body?.meta?.message).isEqualTo(wrongPassword.body?.meta?.message) },
            )
        }

        @DisplayName("새 비밀번호가 기존 비밀번호와 같으면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange
            signUp()
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_PASSWORD,
                HttpMethod.PUT,
                changePasswordEntity(
                    request = changePasswordRequest(newPassword = "Loopers1!"),
                    loginId = "loopers01",
                ),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("새 비밀번호가 규칙을 위반하면, 400 BAD_REQUEST 를 반환한다.")
        @ParameterizedTest
        @ValueSource(strings = ["Abc19900101!", "abcdefgh", "Ab1!"])
        fun returnsBadRequest_whenNewPasswordViolatesPolicy(newPassword: String) {
            // arrange
            signUp()
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_PASSWORD,
                HttpMethod.PUT,
                changePasswordEntity(
                    request = changePasswordRequest(newPassword = newPassword),
                    loginId = "loopers01",
                ),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("X-Loopers-LoginId 헤더가 없으면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenHeaderIsMissing() {
            // arrange
            signUp()
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_PASSWORD,
                HttpMethod.PUT,
                changePasswordEntity(),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("변경에 성공해도, 응답 본문에 평문 비밀번호가 노출되지 않는다.")
        @Test
        fun doesNotExposePassword_whenChangeSucceeds() {
            // arrange
            signUp()

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_PASSWORD,
                HttpMethod.PUT,
                changePasswordEntity(loginId = "loopers01"),
                String::class.java,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body).doesNotContain("Loopers1!") },
                { assertThat(response.body).doesNotContain("Loopers2@") },
                { assertThat(response.body).doesNotContain("password") },
            )
        }
    }
}
