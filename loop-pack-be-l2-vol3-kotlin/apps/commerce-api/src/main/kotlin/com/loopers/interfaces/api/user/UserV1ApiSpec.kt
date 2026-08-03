package com.loopers.interfaces.api.user

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User V1 API", description = "Loopers 유저 API 입니다.")
interface UserV1ApiSpec {
    @Operation(
        summary = "회원 가입",
        description = "로그인 ID, 비밀번호, 이름, 생년월일, 이메일을 받아 신규 회원을 등록합니다.",
    )
    fun signUp(
        @Schema(name = "회원가입 요청", description = "회원가입에 필요한 정보")
        request: UserV1Dto.SignUpRequest,
    ): ApiResponse<UserV1Dto.UserResponse>
}
