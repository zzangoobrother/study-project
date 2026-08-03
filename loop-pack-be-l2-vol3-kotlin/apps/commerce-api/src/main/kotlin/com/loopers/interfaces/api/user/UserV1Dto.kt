package com.loopers.interfaces.api.user

import com.loopers.application.user.UserInfo
import com.loopers.domain.user.UserCommand

class UserV1Dto {
    /**
     * 생년월일을 String 으로 받는다.
     * LocalDate 로 역직렬화하면 Jackson 이 먼저 예외를 던져
     * 도메인의 yyyy-MM-dd 검증이 동작할 기회가 없어진다.
     */
    data class SignUpRequest(
        val loginId: String,
        val password: String,
        val name: String,
        val birthDate: String,
        val email: String,
    ) {
        fun toCommand(): UserCommand.SignUp {
            return UserCommand.SignUp(
                loginId = loginId,
                password = password,
                name = name,
                birthDate = birthDate,
                email = email,
            )
        }

        // data class 가 자동 생성하는 toString() 은 평문 비밀번호를 그대로 노출하므로 직접 재정의한다.
        override fun toString(): String =
            "SignUpRequest(loginId=$loginId, password=****, name=$name, birthDate=$birthDate, email=$email)"
    }

    data class UserResponse(
        val id: Long,
        val loginId: String,
        val name: String,
        val birthDate: String,
        val email: String,
    ) {
        companion object {
            fun from(info: UserInfo): UserResponse {
                return UserResponse(
                    id = info.id,
                    loginId = info.loginId,
                    name = info.name,
                    birthDate = info.birthDate.toString(),
                    email = info.email,
                )
            }
        }
    }
}
