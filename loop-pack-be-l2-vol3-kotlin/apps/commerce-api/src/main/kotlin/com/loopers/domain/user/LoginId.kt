package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable

/** 로그인 ID. 영문 및 숫자 10자 이내. */
@Embeddable
data class LoginId(val value: String) {
    init {
        if (!LOGIN_ID_REGEX.matches(value)) {
            throw CoreException(ErrorType.BAD_REQUEST, "로그인 ID 는 영문 및 숫자 10자 이내여야 합니다.")
        }
    }

    override fun toString(): String = value

    companion object {
        private val LOGIN_ID_REGEX = "^[a-zA-Z0-9]{1,10}$".toRegex()
    }
}
