package com.example.service;

import com.example.exception.CouponIssueException;
import com.example.exception.ErrorCode;
import com.example.model.Coupon;
import com.example.model.CouponIssue;
import com.example.repository.mysql.CouponIssueJpaRepository;
import com.example.repository.mysql.CouponIssueRepository;
import com.example.repository.mysql.CouponJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.exception.ErrorCode.COUPON_NOT_EXIST;

@RequiredArgsConstructor
@Service
public class CouponIssueService {

    private final CouponJpaRepository couponJpaRepository;
    private final CouponIssueJpaRepository couponIssueJpaRepository;
    private final CouponIssueRepository couponIssueRepository;

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

    @Transactional
    public void issue(long couponId, long userId) {
        Coupon coupon = findCoupon(couponId);
        coupon.issue();
        saveCouponIssue(couponId, userId);
    }

    @Transactional(readOnly = true)
    public Coupon findCoupon(long couponId) {
        return couponJpaRepository.findById(couponId).orElseThrow(() -> {
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
