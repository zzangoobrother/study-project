package com.loopers.interfaces.api.user

import com.loopers.application.user.UserFacade
import com.loopers.domain.user.LoginId
import com.loopers.interfaces.api.ApiResponse
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
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
     *
     * 주의: 이 API 는 인증을 수행하지 않는다. 헤더 값의 형식만 검증할 뿐 요청자가 본인인지 확인하지 않으므로,
     * 로그인 ID 를 아는 누구나 타인의 이메일·생년월일을 조회할 수 있다.
     * 의도된 범위 제외이며(설계 문서 3.1 장), X-Loopers-LoginPw 검증이 추가되기 전까지 외부에 공개해서는 안 된다.
     */
    @GetMapping("/me")
    override fun getMyInfo(
        @RequestHeader(HEADER_LOGIN_ID) loginId: String,
        response: HttpServletResponse,
    ): ApiResponse<UserV1Dto.MeResponse> {
        // 응답이 URL 이 아닌 헤더에 따라 달라지므로, Vary 없이는 공유 캐시가 다른 사용자에게 이 응답을 재사용한다.
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        response.setHeader(HttpHeaders.VARY, HEADER_LOGIN_ID)

        return userFacade.getMyInfo(LoginId(loginId))
            .let { UserV1Dto.MeResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    companion object {
        /** 조회 대상 회원을 식별하는 헤더. 애노테이션 인자로 쓰이므로 const 여야 한다. */
        const val HEADER_LOGIN_ID = "X-Loopers-LoginId"
    }
}
