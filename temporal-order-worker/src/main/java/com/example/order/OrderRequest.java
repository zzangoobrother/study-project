package com.example.order;

// 주문 입력. 워크플로/액티비티 인자 및 컨트롤러 요청 본문으로 쓰인다.
public record OrderRequest(String orderId, String productId, int quantity, long amount, String address) {
}
