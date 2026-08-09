package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    /**
     * 신규 회원을 등록한다.
     *
     * 커맨드가 값 객체만 담으므로 이 시점에 포맷 검증은 이미 끝나 있다.
     * 중복 검사와 실제 저장 사이에는 경쟁 상태가 존재하며,
     * 최종 방어선은 login_id 컬럼의 unique 제약이다.
     */
    @Transactional
    fun signUp(command: UserCommand.SignUp): UserModel {
        if (userRepository.existsByLoginId(command.loginId)) {
            throw CoreException(
                errorType = ErrorType.CONFLICT,
                customMessage = "[loginId = ${command.loginId}] 이미 가입된 로그인 ID 입니다.",
            )
        }

        val user = UserModel.create(
            loginId = command.loginId,
            rawPassword = command.password,
            name = command.name,
            birthDate = command.birthDate,
            email = command.email,
            passwordEncoder = passwordEncoder,
        )
        return userRepository.save(user)
    }

    /**
     * 로그인 ID 로 회원을 조회한다.
     *
     * 회원이 없을 때 예외를 던지지 않고 null 을 반환한다.
     * 도메인 서비스는 "없다" 는 사실만 전달하고, 그것을 오류로 볼지는 유스케이스가 정한다.
     */
    @Transactional(readOnly = true)
    fun getUser(loginId: LoginId): UserModel? {
        return userRepository.findByLoginId(loginId)
    }

    /**
     * 비밀번호를 교체한다.
     *
     * 회원이 없을 때 null 을 반환하는 getUser 와 달리 여기서는 곧바로 UNAUTHORIZED 를 던진다.
     * 조회 유스케이스에서는 "없음" 을 어떻게 볼지 상위가 정할 여지가 있지만,
     * 자격 증명 검증에서 "그런 회원이 없다" 는 곧 "자격 증명이 틀렸다" 이며 달리 해석할 여지가 없다.
     *
     * 미가입·소프트 삭제·비밀번호 불일치가 모두 같은 응답이 되도록 메시지는 UserModel 의 상수를 공유한다.
     * findByLoginId 가 소프트 삭제된 회원을 이미 제외하므로 탈퇴 회원은 자동으로 이 경로를 탄다.
     */
    @Transactional
    fun changePassword(command: UserCommand.ChangePassword) {
        val user = userRepository.findByLoginId(command.loginId)
            ?: throw CoreException(
                errorType = ErrorType.UNAUTHORIZED,
                customMessage = UserModel.INVALID_CREDENTIAL_MESSAGE,
            )

        user.changePassword(
            currentPassword = command.currentPassword,
            newPassword = command.newPassword,
            passwordEncoder = passwordEncoder,
        )
        // 영속 상태의 엔티티이므로 커밋 시점에 변경 감지로 UPDATE 된다. save() 는 no-op 이라 호출하지 않는다.
    }
}
