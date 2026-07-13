package com.example.inventory;

// 재고 예약 취소(보상) 요청 DTO
public record InventoryReleaseRequest(String orderId, String reservationId) {
}
