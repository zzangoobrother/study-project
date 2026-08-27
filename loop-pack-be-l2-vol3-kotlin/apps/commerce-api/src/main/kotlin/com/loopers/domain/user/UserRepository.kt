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

    /**
     * 삭제 여부와 무관하게 IN 절로 조회한다. 어드민 전용이다.
     *
     * findByLoginId 와 삭제 행 취급이 정반대인 점에 주의한다. 로그인 화면에서는 탈퇴 회원이 없는 것으로
     * 보여야 하지만, 어드민 주문 목록에서 탈퇴 회원을 결과에서 빼면 "탈퇴한 회원의 주문" 과 "알 수 없는
     * 회원의 주문" 이 둘 다 user = null 로 뭉개진다.
     *
     * findByLoginId 에 플래그를 다는 대신 이름을 나눈 이유는, 그 플래그가 서비스와 파사드를 거쳐
     * 컨트롤러까지 타고 올라가 모든 시그니처를 오염시키기 때문이다.
     * 이름이 다르면 공개 API 경로는 이 메서드의 존재조차 모르는 채로 남는다.
     */
    fun findAllByIdsIncludingDeleted(ids: List<Long>): List<UserModel>
}
