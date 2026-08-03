package com.loopers.domain.user

/**
 * 비밀번호 단방향 암호화 계약.
 *
 * 도메인이 특정 해싱 구현에 의존하지 않도록 인터페이스를 도메인이 소유하고,
 * 실제 구현은 infrastructure 계층에 둔다.
 */
interface PasswordEncoder {
    /** 평문 비밀번호를 저장 가능한 형태로 인코딩한다. */
    fun encode(rawPassword: String): String

    /** 평문 비밀번호가 인코딩된 값과 일치하는지 확인한다. */
    fun matches(rawPassword: String, encodedPassword: String): Boolean
}
