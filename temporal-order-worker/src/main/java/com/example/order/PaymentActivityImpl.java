package com.example.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

// 결제 Activity 구현. downstream 결제 서비스(:8082)를 실제 HTTP로 호출한다.
@Component
public class PaymentActivityImpl implements PaymentActivity {

    private static final Logger log = LoggerFactory.getLogger(PaymentActivityImpl.class);

    private final RestClient restClient;

    public PaymentActivityImpl(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("http://localhost:8082").build();
    }

    @Override
    public PaymentResult pay(OrderRequest request) {
        log.info("결제 Activity 실행: orderId={}, amount={}", request.orderId(), request.amount());
        return restClient.post()
                .uri("/payments")
                .body(Map.of("orderId", request.orderId(), "amount", request.amount()))
                .retrieve()
                .body(PaymentResult.class);
    }

    @Override
    public void cancelPayment(String paymentId, String orderId) {
        log.info("결제 취소(보상) Activity 실행: paymentId={}, orderId={}", paymentId, orderId);
        restClient.post()
                .uri("/payments/cancel")
                .body(Map.of("paymentId", paymentId, "orderId", orderId))
                .retrieve()
                .toBodilessEntity();
    }
}
