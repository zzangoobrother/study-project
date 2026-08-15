package com.loopers.infrastructure.auth

import com.loopers.support.auth.AdminAuthenticator
import com.loopers.support.auth.AdminPrincipal
import org.springframework.stereotype.Component

/**
 * 설정 허용 목록과 대조하는 임시 인증기.
 *
 * 실제 LDAP 디렉터리에 bind 하는 구현체가 생기면 이 클래스를 통째로 대체한다.
 * 허용 목록이 비어 있으면 any 가 false 를 반환해 모든 요청이 거부된다.
 * dev / qa / prd 프로필에는 이 설정을 두지 않으므로 그 환경에서 어드민 API 는 전면 차단된다.
 */
@Component
class StubAdminAuthenticator(
    private val properties: AdminAuthProperties,
) : AdminAuthenticator {
    override fun authenticate(id: String, password: String): AdminPrincipal? {
        val matched = properties.stubCredentials.any { it.id == id && it.password == password }
        return if (matched) AdminPrincipal(id) else null
    }
}
