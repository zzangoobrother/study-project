package com.loopers.interfaces.api

import com.loopers.interfaces.api.user.UserV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
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
}
