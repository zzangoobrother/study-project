package com.loopers.infrastructure.auth

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 스텁 인증기의 허용 목록.
 *
 * CommerceApiApplication 에 @ConfigurationPropertiesScan 이 붙어 있어 별도 등록 설정이 필요 없다.
 * 기본값을 빈 목록으로 두는 것이 실패 폐쇄의 출발점이다 — 설정이 없는 프로필에서는 아무도 통과하지 못한다.
 */
@ConfigurationProperties(prefix = "loopers.admin")
data class AdminAuthProperties(
    val stubCredentials: List<Credential> = emptyList(),
) {
    data class Credential(
        val id: String,
        val password: String,
    )
}
