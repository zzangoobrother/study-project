package com.example.service;

import com.example.repository.redis.RedisRepository;
import com.example.repository.redis.dto.CouponRedisEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class AsyncCouponIssueServiceV2 {

    private final RedisRepository redisRepository;
    private final CouponCacheService couponCacheService;

    /*
        1. totalQuantity > redisRepository.sCard(key); // 쿠폰 발급 수량 제어
        2. !redisRepository.sIsMember(key, String.valueOf(userId)); // 중복 발급 요청 제어
        3. redisRepository.sAdd // 쿠폰 발급 요청 저장
        4. redisRepository.rPush // 쿠폰 발급 큐 적재
        -> 이 모든 과정을 lock 안에서 하지 말고 한꺼번에 하면 되지 않을까?
     */
    public void issue(long couponId, long userId) {
        CouponRedisEntity coupon = couponCacheService.getCouponCache(couponId);
        coupon.checkIssuableCoupon();
        issueRequest(couponId, userId, coupon.totalQuantity());
    }

    private void issueRequest(long couponId, long userId, Integer totalIssueQuantity) {
        if (totalIssueQuantity == null) {
            redisRepository.issueRequest(couponId, userId, Integer.MAX_VALUE);
        }
        redisRepository.issueRequest(couponId, userId, totalIssueQuantity);
    }
}
