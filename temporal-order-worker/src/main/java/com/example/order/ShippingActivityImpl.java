package com.example.order;

import io.temporal.failure.ApplicationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

// 배송 Activity 구현. downstream 배송 서비스(:8082)를 실제 HTTP로 호출한다.
@Component
public class ShippingActivityImpl implements ShippingActivity {

    private static final Logger log = LoggerFactory.getLogger(ShippingActivityImpl.class);

    private final RestClient restClient;

    public ShippingActivityImpl(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("http://localhost:8082").build();
    }

    @Override
    public ShipmentResult arrange(OrderRequest request) {
        log.info("배송 Activity 실행: orderId={}, address={}", request.orderId(), request.address());
        try {
            return restClient.post()
                    .uri("/shipping/arrange")
                    .body(Map.of(
                            "orderId", request.orderId(),
                            "address", request.address() == null ? "" : request.address()))
                    .retrieve()
                    .body(ShipmentResult.class);
        } catch (HttpClientErrorException.Conflict e) {
            // 배송 불가(비즈니스 실패) → 재시도 불가 실패로 변환하여 보상 유도
            throw ApplicationFailure.newNonRetryableFailure(
                    "배송 불가: orderId=" + request.orderId(), "SHIPPING_REJECTED");
        }
    }
}
