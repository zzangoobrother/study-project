package com.loopers.interfaces.api.user

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse

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

    @Operation(
        summary = "내 정보 조회",
        description = "X-Loopers-LoginId 헤더의 로그인 ID 에 해당하는 회원 정보를 반환합니다. 이름은 마지막 글자가 마스킹됩니다. " +
            "(주의: 현재 인증을 수행하지 않습니다.)",
    )
    fun getMyInfo(
        @Schema(name = "로그인 ID", description = "조회할 회원의 로그인 ID. 영문과 숫자만 10자 이내로 허용합니다.")
        loginId: String,
        response: HttpServletResponse,
    ): ApiResponse<UserV1Dto.MeResponse>

    @Operation(
        summary = "비밀번호 수정",
        description = "기존 비밀번호를 확인한 뒤 새 비밀번호로 교체합니다. " +
            "새 비밀번호는 8~16자에 영문·숫자·특수문자를 각각 1자 이상 포함해야 하고, 생년월일을 포함할 수 없으며, " +
            "기존 비밀번호와 같을 수 없습니다.",
    )
    fun changePassword(
        @Schema(name = "로그인 ID", description = "비밀번호를 변경할 회원의 로그인 ID. 영문과 숫자만 10자 이내로 허용합니다.")
        loginId: String,
        @Schema(name = "비밀번호 수정 요청", description = "기존 비밀번호와 새 비밀번호")
        request: UserV1Dto.ChangePasswordRequest,
    ): ApiResponse<Any>
}
