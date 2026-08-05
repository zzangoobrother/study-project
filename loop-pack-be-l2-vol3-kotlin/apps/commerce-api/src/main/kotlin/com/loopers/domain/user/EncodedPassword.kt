package com.loopers.domain.user

import jakarta.persistence.Embeddable

/**
 * 인코딩된 비밀번호.
 *
 * 검증 규칙이 없지만, 같은 String 인 평문과 뒤바뀌는 것을 타입으로 막기 위해 값 객체로 둔다.
 * 인코딩 형식은 PasswordEncoder 구현체의 세부사항이므로 이 클래스는 알지 않는다.
 * 손상된 저장값도 그대로 담을 수 있어야 하므로 공백 여부조차 검사하지 않는다.
 */
@Embeddable
data class EncodedPassword(val value: String) {
    override fun toString(): String = MASKED

    companion object {
        private const val MASKED = "****"
    }
}
