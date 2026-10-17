package com.loopers.domain.payment

import com.loopers.domain.user.LoginId

object PaymentCommand {
    /** 값 객체만 담는다. 만들어졌다면 카드 번호 형식 검증은 끝났다. */
    data class Request(
        val loginId: LoginId,
        val orderId: Long,
        val cardType: CardType,
        val cardNo: CardNo,
    )
}
