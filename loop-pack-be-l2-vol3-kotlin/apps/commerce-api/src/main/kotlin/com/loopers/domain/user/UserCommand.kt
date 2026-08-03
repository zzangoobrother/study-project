package com.loopers.domain.user

/**
 * 유저 도메인의 입력 전달 객체.
 *
 * 도메인에 두어 서비스 시그니처가 상위 계층 타입에 의존하지 않도록 한다.
 */
class UserCommand {
    data class SignUp(
        val loginId: String,
        val password: String,
        val name: String,
        val birthDate: String,
        val email: String,
    )
}
