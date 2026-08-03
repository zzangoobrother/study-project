package com.loopers.application.user

import com.loopers.domain.user.UserModel
import java.time.LocalDate

/**
 * 계층 밖으로 전달되는 유저 정보.
 * 비밀번호는 평문·해시 어떤 형태로도 포함하지 않는다.
 */
data class UserInfo(
    val id: Long,
    val loginId: String,
    val name: String,
    val birthDate: LocalDate,
    val email: String,
) {
    companion object {
        fun from(model: UserModel): UserInfo {
            return UserInfo(
                id = model.id,
                loginId = model.loginId,
                name = model.name,
                birthDate = model.birthDate,
                email = model.email,
            )
        }
    }
}
