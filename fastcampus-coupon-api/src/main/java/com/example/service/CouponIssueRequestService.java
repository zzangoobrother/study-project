package com.example.service;

import com.example.component.DistributeLockExecutor;
import com.example.controller.dto.CouponIssueRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class CouponIssueRequestService {

    private final CouponIssueService couponIssueService;
    private final DistributeLockExecutor distributeLockExecutor;

    public void issueRequestV1(CouponIssueRequestDto requestDto) {
        distributeLockExecutor.execute("lock_" + requestDto.couponId(), 10000, 10000, () -> {
            couponIssueService.issue(requestDto.couponId(), requestDto.userId());
        });
        log.info("쿠폰 발급 완료, couponId : %s, userId : %s".formatted(requestDto.couponId(), requestDto.userId()));
    }
}
