package com.example.order;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface PaymentActivity {

    @ActivityMethod
    PaymentResult pay(OrderRequest request);

    // 보상: 결제 취소
    @ActivityMethod
    void cancelPayment(String paymentId, String orderId);
}
