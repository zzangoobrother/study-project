package com.example.service;

import com.example.component.DistributeLockExecutor;
import com.example.exception.CouponIssueException;
import com.example.exception.ErrorCode;
import com.example.repository.redis.RedisRepository;
import com.example.repository.redis.dto.CouponIssueRequest;
import com.example.repository.redis.dto.CouponRedisEntity;
import com.example.util.CouponRedisUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class AsyncCouponIssueServiceV1 {

    private final RedisRepository redisRepository;
    private final CouponIssueRedisService couponIssueRedisService;
    private final DistributeLockExecutor distributeLockExecutor;
    private final CouponCacheService couponCacheService;

    private final ObjectMapper objectMapper = new ObjectMapper();

//    public void issue(long couponId, long userId) {
//        // 1. 유저의 요청을 Sorted set 적재
//        String key = "issue.request.sorted_set.couponId=%s".formatted(couponId);
//        redisRepository.zAdd(key, String.valueOf(userId), System.currentTimeMillis());
//
//        // 2. 유저의 요청의 순서를 조회
//
//        // 3. 조회 결과를 선착순 조건과 비교
//
//        // 4. 쿠폰 발급 queue에 적재
//    }

    public void issue(long couponId, long userId) {
        CouponRedisEntity coupon = couponCacheService.getCouponCache(couponId);
        coupon.checkIssuableCoupon();

        distributeLockExecutor.execute("lock_%s".formatted(couponId), 3000, 3000, () -> {
            couponIssueRedisService.checkCouponIssueQuantity(coupon, userId);
            issueRequest(couponId, userId);
        });
    }

    private void issueRequest(long couponId, long userId) {
        redisRepository.sAdd(CouponRedisUtils.getIssueRequestKey(couponId), String.valueOf(userId));

        // 쿠폰 발급 큐 적재
        CouponIssueRequest couponIssueRequest = new CouponIssueRequest(couponId, userId);
        String value = null;
        try {
            value = objectMapper.writeValueAsString(couponIssueRequest);
            redisRepository.rPush(CouponRedisUtils.getIssueRequestQueueKey(), value);
        } catch (JsonProcessingException e) {
            throw new CouponIssueException(ErrorCode.FAIL_COUPON_ISSUE_REQUEST, "input: %s".formatted(couponIssueRequest));
        }
    }
}
