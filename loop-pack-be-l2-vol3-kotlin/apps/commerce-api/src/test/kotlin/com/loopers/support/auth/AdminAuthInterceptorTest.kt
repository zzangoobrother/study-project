package com.loopers.support.auth

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class AdminAuthInterceptorTest {
    /** 스텁 인증기 대신 최소 구현을 직접 둔다. 이 테스트의 관심사는 인터셉터의 판정이지 인증 방식이 아니다. */
    private val authenticator = object : AdminAuthenticator {
        override fun authenticate(id: String, password: String): AdminPrincipal? =
            if (id == "admin" && password == "admin1234") AdminPrincipal(id) else null
    }

    private val interceptor = AdminAuthInterceptor(authenticator)
    private val response = MockHttpServletResponse()
    private val handler = Any()

    private fun request(id: String? = null, password: String? = null): MockHttpServletRequest =
        MockHttpServletRequest("GET", "/api-admin/v1/brands").apply {
            id?.let { addHeader(AdminAuthInterceptor.HEADER_LDAP_ID, it) }
            password?.let { addHeader(AdminAuthInterceptor.HEADER_LDAP_PW, it) }
        }

    @DisplayName("어드민 요청을 가로챌 때, ")
    @Nested
    inner class PreHandle {
        @DisplayName("올바른 자격 증명이면, true 를 반환해 요청을 통과시킨다.")
        @Test
        fun returnsTrue_whenCredentialIsValid() {
            // act
            val result = interceptor.preHandle(request("admin", "admin1234"), response, handler)

            // assert
            assertThat(result).isTrue()
        }

        @DisplayName("ID 헤더가 없으면, UNAUTHORIZED 를 던진다.")
        @Test
        fun throwsUnauthorized_whenIdHeaderIsMissing() {
            // act & assert
            assertThatThrownBy { interceptor.preHandle(request(password = "admin1234"), response, handler) }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("비밀번호 헤더가 없으면, UNAUTHORIZED 를 던진다.")
        @Test
        fun throwsUnauthorized_whenPasswordHeaderIsMissing() {
            // act & assert
            assertThatThrownBy { interceptor.preHandle(request(id = "admin"), response, handler) }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("헤더가 빈 문자열이면, UNAUTHORIZED 를 던진다.")
        @Test
        fun throwsUnauthorized_whenHeaderIsBlank() {
            // act & assert
            assertThatThrownBy { interceptor.preHandle(request("", ""), response, handler) }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.UNAUTHORIZED)
        }

        /**
         * 헤더 누락과 자격 증명 불일치가 같은 401 인 것은 의도다.
         * 헤더 누락만 400 으로 구분하면 미인증 요청자에게 어떤 헤더를 채우면 되는지 알려주는 셈이 된다.
         */
        @DisplayName("자격 증명이 틀리면, 헤더 누락과 같은 UNAUTHORIZED 를 던진다.")
        @Test
        fun throwsUnauthorized_whenCredentialIsInvalid() {
            // act & assert
            assertThatThrownBy { interceptor.preHandle(request("admin", "wrong-password"), response, handler) }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.UNAUTHORIZED)
        }
    }
}
