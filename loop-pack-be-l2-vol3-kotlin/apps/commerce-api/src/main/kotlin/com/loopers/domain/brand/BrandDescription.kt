package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable

/**
 * 브랜드 설명. 200자 이내이며 빈 문자열을 허용한다.
 *
 * nullable String 이 아니라 빈 문자열을 허용하는 값 객체로 둔다.
 * "설명 없음" 이 null 과 "" 두 가지로 표현되면 응답 DTO 와 테스트가 두 경우를 모두 다뤄야 한다.
 */
@Embeddable
data class BrandDescription(val value: String) {
    init {
        if (value.length > MAX_LENGTH) {
            throw CoreException(ErrorType.BAD_REQUEST, "브랜드 설명은 ${MAX_LENGTH}자 이내여야 합니다.")
        }
    }

    override fun toString(): String = value

    companion object {
        /** @Column(length = ...) 인자로 쓰이므로 const 여야 한다. */
        const val MAX_LENGTH = 200

        val EMPTY = BrandDescription("")
    }
}
