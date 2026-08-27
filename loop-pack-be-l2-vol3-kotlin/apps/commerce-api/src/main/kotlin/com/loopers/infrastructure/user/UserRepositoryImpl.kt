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

    override fun findAllByIdsIncludingDeleted(ids: List<Long>): List<UserModel> {
        // IN () 은 문법 오류이고, 조회할 대상도 없으므로 쿼리 자체를 보내지 않는다.
        if (ids.isEmpty()) return emptyList()

        return userJpaRepository.findAllByIdIn(ids)
    }
}
