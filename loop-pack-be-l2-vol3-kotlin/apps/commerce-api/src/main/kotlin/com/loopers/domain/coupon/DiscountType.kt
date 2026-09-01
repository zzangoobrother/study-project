package com.loopers.domain.coupon

/**
 * 할인 방식.
 *
 * 계산식을 열거형이 소유하는 이유는 분기가 타입 정의와 한곳에 모이기 때문이다.
 * 협력자가 없어 순수 단위 테스트로 전부 검증된다. ProductSortType 이 from() 을 갖는 것과 같은 배치다.
 */
enum class DiscountType {
    FIXED {
        override fun rawDiscount(discountValue: Long, totalPrice: Long): Long = discountValue
    },

    RATE {
        /**
         * 곱셈을 먼저 하고 나눗셈을 나중에 한다. 순서를 바꾸면 discountValue / 100 이 0 이 되어 할인이 사라진다.
         * Long 나눗셈이라 원 단위 미만은 자동으로 버려진다.
         */
        override fun rawDiscount(discountValue: Long, totalPrice: Long): Long = totalPrice * discountValue / 100
    }, ;

    protected abstract fun rawDiscount(discountValue: Long, totalPrice: Long): Long

    /**
     * 실제 할인 금액. 총액을 넘지 못한다. (설계 문서 5.7 장)
     *
     * 이 상한이 두 경계를 동시에 처리한다 — 총액보다 큰 정액 쿠폰은 총액까지만 깎이고,
     * 최종 결제액의 하한은 0 원이 된다. 초과분은 소멸하며 잔액으로 이월되지 않는다.
     */
    fun calculate(discountValue: Long, totalPrice: Long): Long =
        minOf(rawDiscount(discountValue, totalPrice), totalPrice)
}
