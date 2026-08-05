package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable
import java.time.LocalDate

/**
 * 생년월일.
 *
 * 외부 입력은 "yyyy-MM-dd" 문자열이고 내부 표현은 [LocalDate] 이므로 생성 경로가 둘이다.
 * 문자열 파싱은 [from] 이, 미래 날짜 검증은 생성자가 담당한다.
 */
@Embeddable
data class BirthDate(val value: LocalDate) {
    init {
        if (value.isAfter(LocalDate.now())) {
            throw CoreException(ErrorType.BAD_REQUEST, "생년월일은 미래일 수 없습니다.")
        }
    }

    override fun toString(): String = value.toString()

    companion object {
        private val BIRTH_DATE_REGEX = "^\\d{4}-\\d{2}-\\d{2}$".toRegex()

        fun from(text: String): BirthDate {
            if (!BIRTH_DATE_REGEX.matches(text)) {
                throw CoreException(ErrorType.BAD_REQUEST, "생년월일은 yyyy-MM-dd 형식이어야 합니다.")
            }

            // ISO_LOCAL_DATE 는 STRICT 해석이라 1990-02-30 같은 값을 보정 없이 거부한다.
            val parsed = runCatching { LocalDate.parse(text) }
                .getOrElse { throw CoreException(ErrorType.BAD_REQUEST, "존재하지 않는 생년월일입니다.") }

            return BirthDate(parsed)
        }
    }
}
