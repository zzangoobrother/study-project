package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable

/** 브랜드명. 1~50자이며 공백만으로는 만들 수 없다. */
@Embeddable
data class BrandName(val value: String) {
    init {
        if (value.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어 있을 수 없습니다.")
        }
        if (value.length > MAX_LENGTH) {
            throw CoreException(ErrorType.BAD_REQUEST, "브랜드명은 ${MAX_LENGTH}자 이내여야 합니다.")
        }
    }

    override fun toString(): String = value

    companion object {
        /** @Column(length = ...) 인자로 쓰이므로 const 여야 한다. */
        const val MAX_LENGTH = 50
    }
}
