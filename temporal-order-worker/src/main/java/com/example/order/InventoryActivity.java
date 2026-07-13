package com.example.order;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface InventoryActivity {

    @ActivityMethod
    InventoryResult reserve(OrderRequest request);

    // 보상: 재고 예약 취소
    @ActivityMethod
    void releaseReservation(String orderId, String reservationId);
}
