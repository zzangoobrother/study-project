package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

/**
 * 평문 비밀번호.
 *
 * 저장 대상이 아니므로 @Embeddable 을 붙이지 않는다.
 * data class 를 쓰지 않는 이유는 copy() 와 componentN() 이 평문 유출 표면을 넓히기 때문이다.
 * 평문 전체는 [value] 로만 접근할 수 있으며, 같은 모듈의 PasswordEncoder 구현체만 읽는다.
 */
class RawPassword(internal val value: String) {
    init {
        if (!PASSWORD_REGEX.matches(value)) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "비밀번호는 8~16자이며 영문, 숫자, 특수문자를 각각 1자 이상 포함해야 합니다.",
            )
        }
    }

    /** 평문 전체를 노출하지 않고 부분 일치만 판정할 수 있도록 한다. */
    fun contains(text: String): Boolean = value.contains(text)

    override fun equals(other: Any?): Boolean =
        this === other || (other is RawPassword && value == other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = MASKED

    companion object {
        private const val MASKED = "****"

        /**
         * 8~16자 / 영문·숫자·ASCII 특수문자만 허용 / 세 종류를 각각 1자 이상 포함.
         * 전방 탐색(lookahead)은 문자를 소비하지 않으므로 조건을 독립적으로 겹쳐 걸 수 있다.
         */
        private val PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*\\p{Punct})[A-Za-z\\d\\p{Punct}]{8,16}$".toRegex()
    }
}
