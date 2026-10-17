package com.loopers.domain.payment

/** PG 가 받는 카드사. PG 의 목록과 이름이 같아야 한다 — 어댑터가 name 을 그대로 보낸다. */
enum class CardType {
    SAMSUNG,
    KB,
    HYUNDAI,
}
