package com.example.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 외부 결제 시스템 역할. worker의 결제 Activity가 이곳을 HTTP로 호출한다.
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    @PostMapping
    public PaymentResponse pay(@RequestBody PaymentRequest request) {
        log.info("결제 요청 수신: orderId={}, amount={}", request.orderId(), request.amount());
        // 항상 승인. 의도적 실패 주입은 재고 쪽에서 다룬다.
        String paymentId = "PAY-" + request.orderId();
        return new PaymentResponse(paymentId, "APPROVED");
    }

    // 보상 트랜잭션: 결제 취소(환불). 재고 예약 실패 시 워크플로가 이곳을 호출한다.
    @PostMapping("/cancel")
    public void cancel(@RequestBody PaymentCancelRequest request) {
        log.info("결제 취소 요청 수신(보상): paymentId={}, orderId={}", request.paymentId(), request.orderId());
        // 실제라면 환불 처리. 학습용이라 로그만 남긴다.
    }
}
