package com.loopers.domain.user

interface UserRepository {
    fun save(user: UserModel): UserModel

    /**
     * 소프트 삭제 여부를 고려하지 않는다.
     * DB 의 unique 제약도 삭제 행을 포함해 걸리므로 판정 기준을 일치시킨다.
     */
    fun existsByLoginId(loginId: LoginId): Boolean
}
