package com.loopers.infrastructure.auth

import com.loopers.support.auth.AdminAuthenticator
import com.loopers.support.auth.AdminPrincipal
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 설정 허용 목록과 대조하는 임시 인증기.
 *
 * 실제 LDAP 디렉터리에 bind 하는 구현체가 생기면 이 클래스를 통째로 대체한다.
 * 허용 목록이 비어 있으면 any 가 false 를 반환해 모든 요청이 거부된다.
 * dev / qa / prd 프로필에는 이 설정을 두지 않으므로 그 환경에서 어드민 API 는 전면 차단된다.
 * 단, 이 차단은 배포 시 프로필을 명시적으로 지정하는 것을 전제한다 — 프로필을 빠뜨리면
 * 기본 활성 프로필인 local 의 스텁 자격 증명이 그대로 활성화된다.
 */
@Component
class StubAdminAuthenticator(
    private val properties: AdminAuthProperties,
) : AdminAuthenticator {
    private val log = LoggerFactory.getLogger(StubAdminAuthenticator::class.java)

    @PostConstruct
    fun warnIfEnabled() {
        // 스텁 자격 증명이 살아 있다는 사실이 기동 로그에 남아야, 프로필을 잘못 지정한 배포를 관측할 수 있다.
        if (properties.stubCredentials.isNotEmpty()) {
            log.warn("스텁 어드민 인증기가 활성 상태입니다. 자격 증명 {}건이 설정에 있습니다.", properties.stubCredentials.size)
        }
    }

    override fun authenticate(id: String, password: String): AdminPrincipal? {
        val matched = properties.stubCredentials.any { it.id == id && it.password == password }
        return if (matched) AdminPrincipal(id) else null
    }
}
