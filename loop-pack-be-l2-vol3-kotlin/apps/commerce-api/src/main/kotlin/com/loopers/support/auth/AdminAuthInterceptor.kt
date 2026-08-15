package com.loopers.support.auth

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 어드민 요청의 인증을 담당한다.
 *
 * 컨트롤러마다 @RequestHeader 로 받지 않고 인터셉터로 올린 이유는 누락 가능성 때문이다.
 * @RequestHeader 방식이면 인증 코드가 엔드포인트 10개에 복사되고, 11번째에서 빠뜨려도 컴파일이 통과한다.
 * 경로 패턴으로 걸면 /api-admin 하위의 새 엔드포인트가 자동으로 보호된다.
 *
 * 여기서 던지는 CoreException 은 ApiControllerAdvice 가 잡는다.
 * preHandle 의 예외는 DispatcherServlet 이 HandlerExceptionResolver 체인으로 넘기고
 * @RestControllerAdvice 가 그 체인의 일부이기 때문이며, 덕분에 401 도 공개 API 와 같은 ApiResponse 봉투로 나간다.
 */
@Component
class AdminAuthInterceptor(
    private val adminAuthenticator: AdminAuthenticator,
) : HandlerInterceptor {
    private val log = LoggerFactory.getLogger(AdminAuthInterceptor::class.java)

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val id = request.getHeader(HEADER_LDAP_ID)
        val password = request.getHeader(HEADER_LDAP_PW)

        if (id.isNullOrBlank() || password.isNullOrBlank()) {
            throw CoreException(ErrorType.UNAUTHORIZED)
        }

        val principal = adminAuthenticator.authenticate(id, password)
        if (principal == null) {
            log.warn("어드민 인증 실패 : id={}, uri={}", id, request.requestURI)
            throw CoreException(ErrorType.UNAUTHORIZED)
        }

        log.debug("어드민 인증 성공 : id={}, uri={}", principal.id, request.requestURI)
        return true
    }

    companion object {
        /** 애노테이션 인자와 테스트에서 쓰이므로 const 여야 한다. */
        const val HEADER_LDAP_ID = "X-Loopers-LdapId"
        const val HEADER_LDAP_PW = "X-Loopers-LdapPw"
    }
}
