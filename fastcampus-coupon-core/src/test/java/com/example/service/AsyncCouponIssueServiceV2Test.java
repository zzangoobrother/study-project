package com.example.service;

import com.example.TestConfig;
import com.example.exception.CouponIssueException;
import com.example.exception.ErrorCode;
import com.example.model.Coupon;
import com.example.model.CouponType;
import com.example.repository.mysql.CouponJpaRepository;
import com.example.repository.redis.dto.CouponIssueRequest;
import com.example.util.CouponRedisUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AsyncCouponIssueServiceV2Test extends TestConfig {

    @Autowired
    AsyncCouponIssueServiceV2 sut;

    @Autowired
    RedisTemplate<String, String> redisTemplate;

    @Autowired
    CouponJpaRepository couponJpaRepository;

    @BeforeEach
    void clear() {
        Collection<String> redisKey = redisTemplate.keys("*");
        redisTemplate.delete(redisKey);
    }

    @Test
    @DisplayName("쿠폰 발급 - 쿠폰이 존재하지 않는다면 예외를 발생한다.")
    void issue_1() {
        long couponId= 1;
        long userId = 1;

        CouponIssueException exception = assertThrows(CouponIssueException.class, () -> {
            sut.issue(couponId, userId);
        });

        assertEquals(exception.getErrorCode(), ErrorCode.COUPON_NOT_EXIST);
    }

    @Test
    @DisplayName("쿠폰 발급 - 발급 가능 수량이 존재하지 않는다면 예외를 반환한다.")
    void issue_2() {
        long userId = 1000;
        Coupon coupon = Coupon.builder()
                .couponType(CouponType.FIRST_COME_FIRST_SERVED)
                .title("선착순 테스트 쿠폰")
                .totalQuantity(10)
                .issuedQuantity(0)
                .dateIssueStart(LocalDateTime.now().minusDays(1))
                .dateIssueEnd(LocalDateTime.now().plusDays(1))
                .build();
        Coupon saveCoupon = couponJpaRepository.save(coupon);
        IntStream.range(0, coupon.getTotalQuantity()).forEach(idx -> {
            redisTemplate.opsForSet().add(CouponRedisUtils.getIssueRequestKey(saveCoupon.getId()), String.valueOf(idx));
        });

        CouponIssueException exception = assertThrows(CouponIssueException.class, () -> {
            sut.issue(saveCoupon.getId(), userId);
        });

        assertEquals(exception.getErrorCode(), ErrorCode.INVALID_COUPON_ISSUE_QUANTITY);
    }

    @Test
    @DisplayName("쿠폰 발급 - 이미 발급된 유저라면 예외를 반환한다.")
    void issue_3() {
        long userId = 1;
        Coupon coupon = Coupon.builder()
                .couponType(CouponType.FIRST_COME_FIRST_SERVED)
                .title("선착순 테스트 쿠폰")
                .totalQuantity(10)
                .issuedQuantity(0)
                .dateIssueStart(LocalDateTime.now().minusDays(1))
                .dateIssueEnd(LocalDateTime.now().plusDays(1))
                .build();
        couponJpaRepository.save(coupon);
        redisTemplate.opsForSet().add(CouponRedisUtils.getIssueRequestKey(coupon.getId()), String.valueOf(userId));

        CouponIssueException exception = assertThrows(CouponIssueException.class, () -> {
            sut.issue(coupon.getId(), userId);
        });

        assertEquals(exception.getErrorCode(), ErrorCode.DUPLICATED_COUPON_ISSUE);
    }

    @Test
    @DisplayName("쿠폰 발급 - 발급 기한이 유효하지 않다면 예외를 반환한다.")
    void issue_4() {
        long userId = 1;
        Coupon coupon = Coupon.builder()
                .couponType(CouponType.FIRST_COME_FIRST_SERVED)
                .title("선착순 테스트 쿠폰")
                .totalQuantity(10)
                .issuedQuantity(0)
                .dateIssueStart(LocalDateTime.now().plusDays(1))
                .dateIssueEnd(LocalDateTime.now().plusDays(2))
                .build();
        Coupon saveCoupon = couponJpaRepository.save(coupon);
        redisTemplate.opsForSet().add(CouponRedisUtils.getIssueRequestKey(saveCoupon.getId()), String.valueOf(userId));

        CouponIssueException exception = assertThrows(CouponIssueException.class, () -> {
            sut.issue(saveCoupon.getId(), userId);
        });

        assertEquals(exception.getErrorCode(), ErrorCode.INVALID_COUPON_ISSUE_DATE);
    }

    @Test
    @DisplayName("쿠폰 발급 - 쿠폰 발급을 기록한다.")
    void issue_5() {
        long userId = 1;
        Coupon coupon = Coupon.builder()
                .couponType(CouponType.FIRST_COME_FIRST_SERVED)
                .title("선착순 테스트 쿠폰")
                .totalQuantity(10)
                .issuedQuantity(0)
                .dateIssueStart(LocalDateTime.now().minusDays(1))
                .dateIssueEnd(LocalDateTime.now().plusDays(2))
                .build();
        Coupon saveCoupon = couponJpaRepository.save(coupon);

        sut.issue(saveCoupon.getId(), userId);

        Boolean isSaved = redisTemplate.opsForSet().isMember(CouponRedisUtils.getIssueRequestKey(saveCoupon.getId()), String.valueOf(userId));

        assertTrue(isSaved);
    }

    @Test
    @DisplayName("쿠폰 발급 - 쿠폰 발급 요청이 성공하면 쿠폰 발급 큐에 적재된다.")
    void issue_6() throws JsonProcessingException {
        long userId = 1;
        Coupon coupon = Coupon.builder()
                .couponType(CouponType.FIRST_COME_FIRST_SERVED)
                .title("선착순 테스트 쿠폰")
                .totalQuantity(10)
                .issuedQuantity(0)
                .dateIssueStart(LocalDateTime.now().minusDays(1))
                .dateIssueEnd(LocalDateTime.now().plusDays(2))
                .build();
        Coupon saveCoupon = couponJpaRepository.save(coupon);
        CouponIssueRequest request = new CouponIssueRequest(saveCoupon.getId(), userId);

        sut.issue(saveCoupon.getId(), userId);

        String savedIssueRequest = redisTemplate.opsForList().leftPop(CouponRedisUtils.getIssueRequestQueueKey());

        assertEquals(new ObjectMapper().writeValueAsString(request), savedIssueRequest);
    }
}
