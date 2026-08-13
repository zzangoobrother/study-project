package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

/**
 * 상품 목록 정렬 기준.
 *
 * enum 이름과 쿼리 파라미터 표기를 parameter 필드로 분리한다.
 * valueOf(parameter.uppercase()) 로 처리하면 파라미터 표기가 enum 이름에 묶여,
 * 나중에 표기만 바꾸고 싶을 때 enum 이름까지 바꿔야 한다.
 */
enum class ProductSortType(val parameter: String) {
    LATEST("latest"),
    PRICE_ASC("price_asc"),
    LIKES_DESC("likes_desc"),
    ;

    companion object {
        val DEFAULT = LATEST

        /**
         * 파라미터가 생략되면 기본값을 쓰고, 알 수 없는 값이면 400 을 던진다.
         *
         * 조용히 기본값으로 폴백하지 않는 이유는, sort 가 클라이언트 코드에 박힌 고정 상수 집합이기 때문이다.
         * 오타는 곧 클라이언트의 버그이며 시간이 지난다고 유효해지지 않는다.
         * 폴백하면 개발자가 정렬이 적용됐다고 믿은 채로 배포한다.
         */
        fun from(parameter: String?): ProductSortType {
            if (parameter == null) return DEFAULT

            return entries.find { it.parameter == parameter }
                ?: throw CoreException(
                    errorType = ErrorType.BAD_REQUEST,
                    customMessage = "지원하지 않는 정렬 기준입니다. 사용 가능한 값 : [${entries.joinToString(", ") { it.parameter }}]",
                )
        }
    }
}
