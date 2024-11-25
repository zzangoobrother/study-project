package com.example.service;

import com.example.exception.CouponIssueException;
import com.example.exception.ErrorCode;
import com.example.model.Coupon;
import com.example.model.CouponIssue;
import com.example.model.event.CouponIssueCompleteEvent;
import com.example.repository.mysql.CouponIssueJpaRepository;
import com.example.repository.mysql.CouponIssueRepository;
import com.example.repository.mysql.CouponJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.exception.ErrorCode.COUPON_NOT_EXIST;

@RequiredArgsConstructor
@Service
public class CouponIssueService {

    private final CouponJpaRepository couponJpaRepository;
    private final CouponIssueJpaRepository couponIssueJpaRepository;
    private final CouponIssueRepository couponIssueRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    /*
        트랜잭션 시작

        lock 획득
        Coupon coupon = findCoupon(couponId);
        coupon.issue();
        saveCouponIssue(couponId, userId);
        lock 반납

        1번 요청

        트랜잭션 커밋

        트랜잭션 커밋 전 2번째 요청이 오기 때문에 문제 발생
        때문에 트랜잭션 내부에서 lock 을 하는 행위는 주의 요함
     */
//    @Transactional
//    public void issue(long couponId, long userId) {
//        synchronized (this) {
//            Coupon coupon = findCoupon(couponId);
//            coupon.issue();
//            saveCouponIssue(couponId, userId);
//        }
//    }

    /*
        Sorted Set 사용 시나리오
        1. 유저 요청이 들어옴
        2. 쿠폰 캐시를 통한 유효성 검증
            a. 쿠폰 존재 확인
            b. 쿠폰 유효 시간 확인
        3. Sorted Set에 요청 추가 (ZADD score = time stamp)
            a. ZADD의 응답 값 기반 중복 검사
        4. 현재 요청의 순서 조회(ZRANK) 및 발급 성공 여부 응답
        5. 발급에 성공했다면 쿠폰 발급 queue에 적재

        score 에서 time stamp 사용시 중복 발생,
        이를 해결 해야함
        그리고 O(logN) 의 시간을 가지면서 갯수가 늘수록 속도도 느려짐

        Set 사용 시나리오
        1. 유저 요청이 들어옴
        2. 쿠폰 캐시를 통한 유효성 검증
            a. 쿠폰 존재 확인
            b. 쿠폰 유효 시간 확인
        3. 중복 발급 요청 확인 (SISMEMBER)
        4. 수량 조회(SCARD) 및 발급 가능 여부 검증
        5. 요청 추가(SADD)
        6. 쿠폰 발급 queue에 적재
     */

    @Transactional
    public void issue(long couponId, long userId) {
        Coupon coupon = findCouponWithLock(couponId);
        coupon.issue();
        saveCouponIssue(couponId, userId);
        publishCouponEvent(coupon);
    }

    private void publishCouponEvent(Coupon coupon) {
        if (coupon.isIssueComplete()) {
            applicationEventPublisher.publishEvent(new CouponIssueCompleteEvent(coupon.getId()));
        }
    }

    @Transactional(readOnly = true)
    public Coupon findCoupon(long couponId) {
        return couponJpaRepository.findById(couponId).orElseThrow(() -> {
            throw new CouponIssueException(COUPON_NOT_EXIST, "쿠폰 정책이 존재하지 않습니다. %s".formatted(couponId));
        });
    }

    @Transactional
    public Coupon findCouponWithLock(long couponId) {
        return couponJpaRepository.findCouponWithLock(couponId).orElseThrow(() -> {
            throw new CouponIssueException(COUPON_NOT_EXIST, "쿠폰 정책이 존재하지 않습니다. %s".formatted(couponId));
        });
    }

    @Transactional
    public CouponIssue saveCouponIssue(long couponId, long userId) {
        checkAlreadyIssuance(couponId, userId);
        CouponIssue couponIssue = CouponIssue.builder()
                .couponId(couponId)
                .userId(userId)
                .build();

        return couponIssueJpaRepository.save(couponIssue);
    }

    private void checkAlreadyIssuance(long couponId, long userId) {
        CouponIssue issue = couponIssueRepository.findFirstCouponIssue(couponId, userId);
        if (issue != null) {
            throw new CouponIssueException(ErrorCode.DUPLICATED_COUPON_ISSUE, "이미 발급된 쿠폰입니다. user_id : %s, coupon_id : %s".formatted(couponId, userId));
        }
    }
}
