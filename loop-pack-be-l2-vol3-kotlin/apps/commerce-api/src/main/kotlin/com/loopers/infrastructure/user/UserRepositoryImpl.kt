package com.loopers.infrastructure.user

import com.loopers.domain.user.LoginId
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserRepository
import org.springframework.stereotype.Component

@Component
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {
    override fun save(user: UserModel): UserModel {
        return userJpaRepository.save(user)
    }

    override fun existsByLoginId(loginId: LoginId): Boolean {
        return userJpaRepository.existsByLoginId(loginId)
    }

    // 도메인 계약은 deletedAt 이라는 영속화 세부사항을 몰라도 되도록, 이름을 findByLoginId 로 좁혀 노출한다.
    override fun findByLoginId(loginId: LoginId): UserModel? {
        return userJpaRepository.findByLoginIdAndDeletedAtIsNull(loginId)
    }
}
