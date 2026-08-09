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

    /**
     * 비밀번호를 교체한다.
     *
     * 판정이 전부 도메인에 있어 단순 위임이지만 계층을 건너뛰지 않는다.
     * 컨트롤러가 도메인 서비스를 직접 참조하기 시작하면 유스케이스 정책이 생길 자리가 사라진다.
     */
    fun changePassword(command: UserCommand.ChangePassword) {
        userService.changePassword(command)
    }
}
