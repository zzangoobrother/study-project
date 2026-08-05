package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable

/** 회원 이름. 한글 또는 영문 20자 이내. */
@Embeddable
data class UserName(val value: String) {
    init {
        if (!NAME_REGEX.matches(value)) {
            throw CoreException(ErrorType.BAD_REQUEST, "이름은 한글 또는 영문 20자 이내여야 합니다.")
        }
    }

    override fun toString(): String = value

    companion object {
        private val NAME_REGEX = "^[가-힣a-zA-Z]{1,20}$".toRegex()
    }
}
