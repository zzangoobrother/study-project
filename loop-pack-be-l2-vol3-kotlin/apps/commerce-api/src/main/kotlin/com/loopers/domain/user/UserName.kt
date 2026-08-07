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

    /**
     * 마지막 글자를 마스킹 문자로 가린 이름.
     *
     * 1글자 이름은 전체가 가려진다. NAME_REGEX 가 1자를 허용하므로 발생 가능하며, 예외로 두지 않는다.
     * NAME_REGEX 가 한글·영문만 허용해 모든 문자가 BMP 안에 있으므로 dropLast(1) 로 안전하다.
     * 반환 타입이 String 인 이유는 마스킹된 값이 NAME_REGEX 를 만족하지 않아 UserName 으로 감쌀 수 없기 때문이다.
     */
    fun masked(): String = value.dropLast(1) + MASK_CHAR

    override fun toString(): String = value

    companion object {
        private val NAME_REGEX = "^[가-힣a-zA-Z]{1,20}$".toRegex()

        /** 마스킹 문자. 프로젝트 전체에서 * 로 통일한다. */
        private const val MASK_CHAR = '*'
    }
}
