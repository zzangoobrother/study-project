package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable

/**
 * 카드 번호. PG 와 같은 형식(xxxx-xxxx-xxxx-xxxx)을 우리 쪽에서 먼저 막는다.
 * 형식 오류를 PG 까지 보내면 4xx 가 돌아오는데, 그건 PG 장애가 아니라 우리 입력 문제이므로 PG 를 부르기 전에 400 으로 끝낸다.
 *
 * toString 이 마지막 네 자리만 남기는 이유는 로그 · 예외 메시지에 카드 번호 전체가 찍히지 않게 하기 위해서다.
 */
@Embeddable
data class CardNo(val value: String) {
    init {
        if (!PATTERN.matches(value)) {
            throw CoreException(ErrorType.BAD_REQUEST, "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다.")
        }
    }

    override fun toString(): String = "****-****-****-${value.takeLast(4)}"

    companion object {
        private val PATTERN = Regex("^\\d{4}-\\d{4}-\\d{4}-\\d{4}$")
    }
}
