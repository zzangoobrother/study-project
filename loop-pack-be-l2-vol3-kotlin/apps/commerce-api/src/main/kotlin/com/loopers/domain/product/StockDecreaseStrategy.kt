package com.loopers.domain.product

/**
 * 재고 차감 락 전략의 추상. ProductRepository.decreaseStock 계약을 그대로 옮겨 담는다 —
 * 전략을 갈아 끼워도 OrderFacade → ProductService → ProductRepository 호출부가
 * 한 글자도 바뀌지 않는 것이 이 비교의 조건이다. (2026-09-09 설계 문서 6.1 장)
 *
 * 기동 시 loopers.stock.lock-strategy 값에 따라 구현 셋(조건부 UPDATE · 낙관적 락 · 비관적 락)
 * 중 하나만 빈으로 올라간다. ProductRepositoryImpl 이 이 인터페이스에 위임하는 것이 스위치의 실체다.
 */
interface StockDecreaseStrategy {
    fun decreaseStock(productId: Long, quantity: Int): Int
}
