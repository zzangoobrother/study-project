package com.example.order;

// 재고 Activity 결과. downstream 응답(reservationId, status)과 필드가 일치.
public record InventoryResult(String reservationId, String status) {
}
