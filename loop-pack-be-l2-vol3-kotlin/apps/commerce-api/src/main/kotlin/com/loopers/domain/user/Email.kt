package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable

/** 이메일 주소. xx@yy.zz 형식이며 254자를 넘을 수 없다. */
@Embeddable
data class Email(val value: String) {
    init {
        if (!EMAIL_REGEX.matches(value)) {
            throw CoreException(ErrorType.BAD_REQUEST, "이메일은 xx@yy.zz 형식이어야 합니다.")
        }
        if (value.length > MAX_LENGTH) {
            throw CoreException(ErrorType.BAD_REQUEST, "이메일은 ${MAX_LENGTH}자를 넘을 수 없습니다.")
        }
    }

    override fun toString(): String = value

    companion object {
        private val EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()

        /** RFC 5321 이 정의하는 이메일 주소 최대 길이. */
        private const val MAX_LENGTH = 254
    }
}
