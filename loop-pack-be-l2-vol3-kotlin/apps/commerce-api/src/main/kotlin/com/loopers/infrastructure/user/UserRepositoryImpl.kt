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

    // UserRepository 도메인 인터페이스는 아직 String 을 받으므로 여기서 값 객체로 감싼다.
    // 이 래핑은 UserService 의 임시 래핑과 마찬가지로 Task 5 에서 제거된다.
    override fun existsByLoginId(loginId: String): Boolean {
        return userJpaRepository.existsByLoginId(LoginId(loginId))
    }
}
