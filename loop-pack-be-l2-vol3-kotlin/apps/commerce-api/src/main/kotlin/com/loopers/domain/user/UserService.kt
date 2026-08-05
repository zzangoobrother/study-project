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
}
