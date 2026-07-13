package com.example.order;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;

import java.time.Duration;

// 주문 오케스트레이션 + Saga 보상. 결제 → 재고 → 배송. 실패 시 보상을 역순 실행.
public class OrderWorkflowImpl implements OrderWorkflow {

    private final PaymentActivity paymentActivity = Workflow.newActivityStub(
            PaymentActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(10))
                    .build());

    private final InventoryActivity inventoryActivity = Workflow.newActivityStub(
            InventoryActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(5))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setBackoffCoefficient(2.0)
                            .setMaximumInterval(Duration.ofSeconds(10))
                            .setMaximumAttempts(5)
                            .build())
                    .build());

    private final ShippingActivity shippingActivity = Workflow.newActivityStub(
            ShippingActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(5))
                    .build());

    @Override
    public OrderResult processOrder(OrderRequest request) {
        Saga saga = new Saga(new Saga.Options.Builder().build());
        try {
            // 1) 결제
            PaymentResult payment = paymentActivity.pay(request);
            saga.addCompensation(() -> paymentActivity.cancelPayment(payment.paymentId(), request.orderId()));

            // 2) 재고 예약
            InventoryResult inventory = inventoryActivity.reserve(request);
            saga.addCompensation(() -> inventoryActivity.releaseReservation(request.orderId(), inventory.reservationId()));

            // 3) 배송 준비
            ShipmentResult shipment = shippingActivity.arrange(request);

            return new OrderResult(request.orderId(), payment.paymentId(),
                    inventory.reservationId(), shipment.shipmentId(), "COMPLETED");
        } catch (RuntimeException e) {
            // 실패: 등록된 보상을 역순 실행 (재고 취소 → 결제 취소)
            saga.compensate();
            throw e;
        }
    }
}
