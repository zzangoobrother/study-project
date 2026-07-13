package com.example.inventory;

// 재고 예약 요청 DTO
public record InventoryRequest(String orderId, String productId, int quantity) {
}
