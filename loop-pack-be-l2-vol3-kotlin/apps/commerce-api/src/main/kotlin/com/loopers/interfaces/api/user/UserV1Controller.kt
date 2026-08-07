package com.loopers.interfaces.api.user

import com.loopers.application.user.UserFacade
import com.loopers.domain.user.LoginId
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserV1Controller(
    private val userFacade: UserFacade,
) : UserV1ApiSpec {
    @PostMapping
    override fun signUp(
        @RequestBody request: UserV1Dto.SignUpRequest,
    ): ApiResponse<UserV1Dto.UserResponse> {
        return userFacade.signUp(request.toCommand())
            .let { UserV1Dto.UserResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    /**
     * 헤더 값을 LoginId 로 감싸는 것만으로 "영문과 숫자만" 검증이 수행된다.
     * 위반 시 LoginId 생성자가 CoreException(BAD_REQUEST) 를 던지므로 별도 검증 코드를 두지 않는다.
     */
    @GetMapping("/me")
    override fun getMyInfo(
        @RequestHeader(HEADER_LOGIN_ID) loginId: String,
    ): ApiResponse<UserV1Dto.MeResponse> {
        return userFacade.getMyInfo(LoginId(loginId))
            .let { UserV1Dto.MeResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    companion object {
        /** 조회 대상 회원을 식별하는 헤더. 애노테이션 인자로 쓰이므로 const 여야 한다. */
        const val HEADER_LOGIN_ID = "X-Loopers-LoginId"
    }
}
