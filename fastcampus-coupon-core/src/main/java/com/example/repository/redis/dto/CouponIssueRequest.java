package com.example.repository.redis.dto;

public record CouponIssueRequest(
        long couponId,
        long userId
) {
}
