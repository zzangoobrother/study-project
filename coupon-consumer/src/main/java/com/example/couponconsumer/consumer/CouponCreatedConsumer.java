package com.example.couponconsumer.consumer;

import com.example.couponconsumer.domain.Coupon;
import com.example.couponconsumer.domain.FailedEvent;
import com.example.couponconsumer.repository.CouponRepository;
import com.example.couponconsumer.repository.FailedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CouponCreatedConsumer {
    private final Logger logger = LoggerFactory.getLogger(CouponCreatedConsumer.class);

    private final CouponRepository couponRepository;
    private final FailedEventRepository failedEventRepository;

    public CouponCreatedConsumer(CouponRepository couponRepository, FailedEventRepository failedEventRepository) {
        this.couponRepository = couponRepository;
        this.failedEventRepository = failedEventRepository;
    }

    @KafkaListener(topics = "coupon_create", groupId = "group_1")
    public void listener(Long userId) {
        try {
            couponRepository.save(new Coupon(userId));
        } catch (Exception e) {
            logger.error("failed to create coupon::{}", userId);
            failedEventRepository.save(new FailedEvent(userId));
        }
    }
}
