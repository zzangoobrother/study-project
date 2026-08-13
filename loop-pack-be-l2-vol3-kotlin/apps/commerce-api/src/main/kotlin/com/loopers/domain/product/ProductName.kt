package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable

/** 상품명. 1~100자이며 공백만으로는 만들 수 없다. */
@Embeddable
data class ProductName(val value: String) {
    init {
        if (value.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "상품명은 비어 있을 수 없습니다.")
        }
        if (value.length > MAX_LENGTH) {
            throw CoreException(ErrorType.BAD_REQUEST, "상품명은 ${MAX_LENGTH}자 이내여야 합니다.")
        }
    }

    override fun toString(): String = value

    companion object {
        /** @Column(length = ...) 인자로 쓰이므로 const 여야 한다. */
        const val MAX_LENGTH = 100
    }
}
