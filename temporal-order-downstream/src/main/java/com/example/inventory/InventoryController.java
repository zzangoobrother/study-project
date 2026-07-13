package com.example.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// 재고 서비스.
//  - 일시적 장애: orderId별 처음 FAIL_TIMES번은 500 (재시도로 복구됨)
//  - 비즈니스 실패: productId "SOLD_OUT"은 항상 409 (재시도 무의미 → 보상 유도)
//  - 보상: /inventory/release 로 예약 취소
@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

    private final ConcurrentHashMap<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

    private static final int FAIL_TIMES = 2;

    @PostMapping("/reserve")
    public InventoryResponse reserve(@RequestBody InventoryRequest request) {
        // 품절: 재시도해도 소용없는 비즈니스 실패
        if ("SOLD_OUT".equals(request.productId())) {
            log.warn("재고 부족(비즈니스 실패): orderId={}, productId={}", request.orderId(), request.productId());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "품절");
        }

        // 일시적 장애 시뮬레이션
        int attempt = attempts.computeIfAbsent(request.orderId(), k -> new AtomicInteger())
                .incrementAndGet();
        if (attempt <= FAIL_TIMES) {
            log.warn("재고 예약 실패(의도적): orderId={}, attempt={}/{}", request.orderId(), attempt, FAIL_TIMES);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "일시적 재고 시스템 오류");
        }

        log.info("재고 예약 성공: orderId={}, attempt={}", request.orderId(), attempt);
        String reservationId = "RES-" + request.orderId();
        return new InventoryResponse(reservationId, "RESERVED");
    }

    // 보상 트랜잭션: 재고 예약 취소
    @PostMapping("/release")
    public void release(@RequestBody InventoryReleaseRequest request) {
        log.info("재고 예약 취소 요청 수신(보상): orderId={}, reservationId={}", request.orderId(), request.reservationId());
        // 실제라면 예약 수량 복구. 학습용이라 로그만 남긴다.
    }
}
