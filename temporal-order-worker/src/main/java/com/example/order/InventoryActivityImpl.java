package com.example.order;

import io.temporal.failure.ApplicationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

// 재고 Activity 구현. downstream 재고 서비스(:8082)를 실제 HTTP로 호출한다.
@Component
public class InventoryActivityImpl implements InventoryActivity {

    private static final Logger log = LoggerFactory.getLogger(InventoryActivityImpl.class);

    private final RestClient restClient;

    public InventoryActivityImpl(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("http://localhost:8082").build();
    }

    @Override
    public InventoryResult reserve(OrderRequest request) {
        log.info("재고 Activity 실행: orderId={}, productId={}, quantity={}",
                request.orderId(), request.productId(), request.quantity());
        try {
            return restClient.post()
                    .uri("/inventory/reserve")
                    .body(Map.of(
                            "orderId", request.orderId(),
                            "productId", request.productId(),
                            "quantity", request.quantity()))
                    .retrieve()
                    .body(InventoryResult.class);
        } catch (HttpClientErrorException e) {
            // 4xx 전체를 non-retryable로 변환. 409(품절)가 대표 케이스지만 그 외 4xx도 영구 오류로 취급한다.
            throw ApplicationFailure.newNonRetryableFailure(
                    "재고 예약 실패: orderId=" + request.orderId() + ", status=" + e.getStatusCode(),
                    "OUT_OF_STOCK");
        }
    }

    @Override
    public void releaseReservation(String orderId, String reservationId) {
        log.info("재고 예약 취소(보상) Activity 실행: orderId={}, reservationId={}", orderId, reservationId);
        restClient.post()
                .uri("/inventory/release")
                .body(Map.of("orderId", orderId, "reservationId", reservationId))
                .retrieve()
                .toBodilessEntity();
    }
}
