package com.loopers.domain.user

interface UserRepository {
    fun save(user: UserModel): UserModel

    /**
     * 소프트 삭제 여부를 고려하지 않는다.
     * DB 의 unique 제약도 삭제 행을 포함해 걸리므로 판정 기준을 일치시킨다.
     */
    fun existsByLoginId(loginId: LoginId): Boolean

    /**
     * 소프트 삭제된 회원은 없는 것으로 취급한다.
     *
     * existsByLoginId 와 삭제 행 취급이 정반대인 점에 주의한다.
     * 로그인 ID 재사용은 막아야 하지만(exists), 탈퇴 회원의 개인정보는 조회되면 안 된다(find).
     */
    fun findByLoginId(loginId: LoginId): UserModel?
}
