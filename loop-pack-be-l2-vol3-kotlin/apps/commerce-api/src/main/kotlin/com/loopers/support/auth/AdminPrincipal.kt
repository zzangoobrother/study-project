package com.loopers.support.auth

/**
 * 인증을 통과한 관리자.
 *
 * 지금은 ID 하나뿐이지만, 실제 LDAP 구현체로 교체되면 bind 결과의 DN 이나 소속 그룹이 여기 들어간다.
 * authenticate 가 Boolean 이 아니라 이 타입을 반환하는 이유는 인증 로그에 "누가" 가 남아야 하기 때문이다.
 */
data class AdminPrincipal(val id: String)
