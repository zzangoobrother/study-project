package com.example.order;

import io.temporal.client.WorkflowException;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

// 주문 Saga 워크플로 단위 테스트.
// TestWorkflowExtension이 인메모리 Temporal 환경/워커/워크플로 스텁을 각 테스트에 주입한다.
// Activity는 Mockito mock으로 대체해 downstream HTTP 호출 없이 오케스트레이션/보상 로직만 검증한다.
class OrderWorkflowTest {

    // setDoNotStart(true): 테스트 안에서 mock Activity를 등록한 뒤 env.start()로 직접 기동한다.
    @RegisterExtension
    static final TestWorkflowExtension ext = TestWorkflowExtension.newBuilder()
            .setWorkflowTypes(OrderWorkflowImpl.class)
            .setDoNotStart(true)
            .build();

    // withoutAnnotations(): mock 클래스에 @ActivityMethod가 상속되면 Temporal이 거부하므로 애노테이션을 제거한다.
    private final PaymentActivity payment = mock(PaymentActivity.class, withSettings().withoutAnnotations());
    private final InventoryActivity inventory = mock(InventoryActivity.class, withSettings().withoutAnnotations());
    private final ShippingActivity shipping = mock(ShippingActivity.class, withSettings().withoutAnnotations());

    private final OrderRequest request =
            new OrderRequest("1001", "BOOK-01", 2, 50000, "서울시 강남구");

    // 시나리오 A: 세 단계 모두 성공 → COMPLETED, 보상은 한 번도 호출되지 않음
    @Test
    void 정상주문이면_COMPLETED이고_보상은_실행되지_않는다(
            TestWorkflowEnvironment env, Worker worker, OrderWorkflow workflow) {
        when(payment.pay(any())).thenReturn(new PaymentResult("PAY-1001", "APPROVED"));
        when(inventory.reserve(any())).thenReturn(new InventoryResult("RES-1001", "RESERVED"));
        when(shipping.arrange(any())).thenReturn(new ShipmentResult("SHIP-1001", "ARRANGED"));
        worker.registerActivitiesImplementations(payment, inventory, shipping);
        env.start();

        OrderResult result = workflow.processOrder(request);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.shipmentId()).isEqualTo("SHIP-1001");
        verify(payment, never()).cancelPayment(any(), any());
        verify(inventory, never()).releaseReservation(any(), any());
    }

    // 시나리오 B: 재고가 일시 장애(재시도 가능 실패)로 2번 실패 후 성공 → 재시도로 복구되어 COMPLETED
    @Test
    void 재고_일시장애는_재시도로_복구되어_COMPLETED이다(
            TestWorkflowEnvironment env, Worker worker, OrderWorkflow workflow) {
        when(payment.pay(any())).thenReturn(new PaymentResult("PAY-1001", "APPROVED"));
        when(inventory.reserve(any()))
                .thenThrow(ApplicationFailure.newFailure("일시적 재고 오류", "TRANSIENT"))
                .thenThrow(ApplicationFailure.newFailure("일시적 재고 오류", "TRANSIENT"))
                .thenReturn(new InventoryResult("RES-1001", "RESERVED"));
        when(shipping.arrange(any())).thenReturn(new ShipmentResult("SHIP-1001", "ARRANGED"));
        worker.registerActivitiesImplementations(payment, inventory, shipping);
        env.start();

        OrderResult result = workflow.processOrder(request);

        assertThat(result.status()).isEqualTo("COMPLETED");
        // RetryOptions에 의해 reserve가 총 3번 호출됨(2번 실패 + 1번 성공)
        verify(inventory, times(3)).reserve(any());
    }

    // 시나리오 C: 재고 품절(재시도 불가 실패) → 결제만 보상, 재고 보상은 없음
    @Test
    void 재고품절이면_결제만_보상하고_재고보상은_없다(
            TestWorkflowEnvironment env, Worker worker, OrderWorkflow workflow) {
        when(payment.pay(any())).thenReturn(new PaymentResult("PAY-1001", "APPROVED"));
        when(inventory.reserve(any())).thenThrow(
                ApplicationFailure.newNonRetryableFailure("품절", "OUT_OF_STOCK"));
        worker.registerActivitiesImplementations(payment, inventory, shipping);
        env.start();

        assertThatThrownBy(() -> workflow.processOrder(request))
                .isInstanceOf(WorkflowException.class);

        // 재고 예약 자체가 실패했으므로 재고 보상은 등록되지 않았음 → 결제 취소만 실행
        verify(payment).cancelPayment(eq("PAY-1001"), eq("1001"));
        verify(inventory, never()).releaseReservation(any(), any());
    }

    // 시나리오 D: 배송 불가(재시도 불가 실패) → 재고취소 → 결제취소 역순(LIFO) 보상
    @Test
    void 배송실패면_재고취소_후_결제취소_순서로_역순보상한다(
            TestWorkflowEnvironment env, Worker worker, OrderWorkflow workflow) {
        when(payment.pay(any())).thenReturn(new PaymentResult("PAY-1001", "APPROVED"));
        when(inventory.reserve(any())).thenReturn(new InventoryResult("RES-1001", "RESERVED"));
        when(shipping.arrange(any())).thenThrow(
                ApplicationFailure.newNonRetryableFailure("배송 불가", "SHIPPING_REJECTED"));
        worker.registerActivitiesImplementations(payment, inventory, shipping);
        env.start();

        assertThatThrownBy(() -> workflow.processOrder(request))
                .isInstanceOf(WorkflowException.class);

        // 실행 순서는 결제→재고였지만, 보상은 등록 역순으로 재고취소 → 결제취소
        InOrder inOrder = inOrder(inventory, payment);
        inOrder.verify(inventory).releaseReservation(eq("1001"), eq("RES-1001"));
        inOrder.verify(payment).cancelPayment(eq("PAY-1001"), eq("1001"));
    }
}
