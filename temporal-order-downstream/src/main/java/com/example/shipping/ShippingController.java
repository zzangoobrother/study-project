package com.example.shipping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// 배송 서비스. address가 "INVALID"이면 비즈니스 실패(409)를 반환해 보상 흐름을 유도한다.
@RestController
@RequestMapping("/shipping")
public class ShippingController {

    private static final Logger log = LoggerFactory.getLogger(ShippingController.class);

    @PostMapping("/arrange")
    public ShippingResponse arrange(@RequestBody ShippingRequest request) {
        if ("INVALID".equals(request.address())) {
            log.warn("배송 불가(비즈니스 실패): orderId={}, address={}", request.orderId(), request.address());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "배송 불가 주소");
        }
        log.info("배송 준비 완료: orderId={}, address={}", request.orderId(), request.address());
        String shipmentId = "SHIP-" + request.orderId();
        return new ShippingResponse(shipmentId, "ARRANGED");
    }
}
