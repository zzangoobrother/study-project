package com.loopers.infrastructure.user

import com.loopers.domain.user.LoginId
import com.loopers.domain.user.UserModel
import org.springframework.data.jpa.repository.JpaRepository

interface UserJpaRepository : JpaRepository<UserModel, Long> {
    fun existsByLoginId(loginId: LoginId): Boolean

    fun findByLoginIdAndDeletedAtIsNull(loginId: LoginId): UserModel?
}
