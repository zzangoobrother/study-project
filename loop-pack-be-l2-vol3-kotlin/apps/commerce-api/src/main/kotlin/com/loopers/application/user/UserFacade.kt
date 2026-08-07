package com.loopers.application.user

import com.loopers.domain.user.LoginId
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class UserFacade(
    private val userService: UserService,
) {
    fun signUp(command: UserCommand.SignUp): UserInfo {
        return userService.signUp(command)
            .let { UserInfo.from(it) }
    }

    /**
     * 로그인 ID 에 해당하는 회원 정보를 조회한다.
     *
     * "회원이 없음" 을 404 로 볼지 결정하는 것은 유스케이스의 책임이므로 이 계층에서 변환한다.
     * 미가입과 소프트 삭제를 구분하지 않는다. 응답 차이로 과거 가입 여부를 유추할 수 없게 한다.
     */
    fun getMyInfo(loginId: LoginId): UserInfo {
        return userService.getUser(loginId)
            ?.let { UserInfo.from(it) }
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[loginId = $loginId] 등록되지 않은 회원입니다.",
            )
    }
}
