package com.loopers.domain.user

/**
 * 유저 도메인의 입력 전달 객체.
 *
 * 도메인에 두어 서비스 시그니처가 상위 계층 타입에 의존하지 않도록 한다.
 * 값 객체만 담으므로 이 객체가 존재한다는 것 자체가 전 필드 검증 통과를 의미한다.
 * 비밀번호 마스킹은 RawPassword 가 담당하므로 toString() 을 재정의하지 않는다.
 */
class UserCommand {
    data class SignUp(
        val loginId: LoginId,
        val password: RawPassword,
        val name: UserName,
        val birthDate: BirthDate,
        val email: Email,
    )

    data class ChangePassword(
        val loginId: LoginId,
        val currentPassword: RawPassword,
        val newPassword: RawPassword,
    )
}
