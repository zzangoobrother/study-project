package com.loopers.support.auth

/**
 * 관리자 인증 이음새.
 *
 * id 와 password 두 인자로 받는 것이 이 인터페이스의 핵심이다.
 * 실제 LDAP 인증은 디렉터리에 그 자격 증명으로 bind 를 시도하는 것이므로,
 * 이 시그니처면 구현체만 갈아끼우고 인터셉터·컨트롤러·설정은 한 줄도 고치지 않는다.
 * authenticate(token: String) 같은 단일 인자였다면 LDAP 로 바꾸는 순간 인터페이스부터 다시 짜야 한다.
 */
interface AdminAuthenticator {
    /** 인증 실패 시 null 을 반환한다. 그것을 401 로 볼지는 호출자가 정한다. */
    fun authenticate(id: String, password: String): AdminPrincipal?
}
