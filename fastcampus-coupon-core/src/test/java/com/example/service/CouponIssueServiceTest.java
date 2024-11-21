package com.example.service;

import com.example.exception.CouponIssueException;
import com.example.exception.ErrorCode;
import com.example.model.Coupon;
import com.example.model.CouponIssue;
import com.example.model.CouponType;
import com.example.repository.mysql.CouponIssueJpaRepository;
import com.example.repository.mysql.CouponIssueRepository;
import com.example.repository.mysql.CouponJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CouponIssueServiceTest extends TestConfig {

    @Autowired
    CouponIssueJpaRepository couponIssueJpaRepository;

    @Autowired
    CouponIssueRepository couponIssueRepository;

    @Autowired
    CouponJpaRepository couponJpaRepository;

    @Autowired
    CouponIssueService sut;

    @BeforeEach
    void clean() {
        couponJpaRepository.deleteAllInBatch();
        couponIssueJpaRepository.deleteAllInBatch();
    }

    @DisplayName("쿠폰 발급 내역이 존재하면 예외를 반환한다,")
    @Test
    void saveCouponIssue_1() {
        CouponIssue couponIssue = CouponIssue.builder()
                .couponId(1L)
                .userId(1L)
                .build();
        couponIssueJpaRepository.save(couponIssue);

        CouponIssueException exception = assertThrows(CouponIssueException.class, () -> {
            sut.saveCouponIssue(couponIssue.getCouponId(), couponIssue.getUserId());
        });

        assertEquals(exception.getErrorCode(), ErrorCode.DUPLICATED_COUPON_ISSUE);
    }

    @DisplayName("쿠폰 발급 내역이 존재하지 않는다면 쿠폰을 발급한다.")
    @Test
    void saveCouponIssue_2() {
        long couponId = 1L;
        long userId = 1L;

        CouponIssue couponIssue = sut.saveCouponIssue(couponId, userId);

        assertTrue(couponIssueJpaRepository.findById(couponIssue.getId()).isPresent());
    }

    @DisplayName("발급 수량, 기한, 중복 발급 문제가 없다면 쿠폰을 발급한다.")
    @Test
    void issue_1() {
        long userId = 1L;
        Coupon coupon = Coupon.builder()
                .couponType(CouponType.FIRST_COME_FIRST_SERVED)
                .title("선착순 테스트 쿠폰")
                .totalQuantity(100)
                .issuedQuantity(0)
                .dateIssueStart(LocalDateTime.now().minusDays(1))
                .dateIssueEnd(LocalDateTime.now().plusDays(1))
                .build();
        couponJpaRepository.save(coupon);

        sut.issue(coupon.getId(), userId);

        Coupon couponResult = couponJpaRepository.findById(coupon.getId()).get();
        assertEquals(couponResult.getIssuedQuantity(), 1);

        CouponIssue couponIssueResult = couponIssueRepository.findFirstCouponIssue(coupon.getId(), userId);
        assertNotNull(couponIssueResult);
    }

    @DisplayName("발급 수량에 문제가 있다면 예외를 반환한다.")
    @Test
    void issue_2() {
        long userId = 1L;
        Coupon coupon = Coupon.builder()
                .couponType(CouponType.FIRST_COME_FIRST_SERVED)
                .title("선착순 테스트 쿠폰")
                .totalQuantity(100)
                .issuedQuantity(100)
                .dateIssueStart(LocalDateTime.now().minusDays(1))
                .dateIssueEnd(LocalDateTime.now().plusDays(1))
                .build();
        couponJpaRepository.save(coupon);

        CouponIssueException exception = assertThrows(CouponIssueException.class, () -> sut.issue(coupon.getId(), userId));
        assertEquals(exception.getErrorCode(), ErrorCode.INVALID_COUPON_ISSUE_QUANTITY);
    }

    @DisplayName("발급 기한에 문제가 있다면 예외를 반환한다.")
    @Test
    void issue_3() {
        long userId = 1L;
        Coupon coupon = Coupon.builder()
                .couponType(CouponType.FIRST_COME_FIRST_SERVED)
                .title("선착순 테스트 쿠폰")
                .totalQuantity(100)
                .issuedQuantity(0)
                .dateIssueStart(LocalDateTime.now().minusDays(2))
                .dateIssueEnd(LocalDateTime.now().minusDays(1))
                .build();
        couponJpaRepository.save(coupon);

        CouponIssueException exception = assertThrows(CouponIssueException.class, () -> sut.issue(coupon.getId(), userId));
        assertEquals(exception.getErrorCode(), ErrorCode.INVALID_COUPON_ISSUE_DATE);
    }

    @DisplayName("중복 발급 검증에 문제가 있다면 예외를 반환한다.")
    @Test
    void issue_4() {
        long userId = 1L;
        Coupon coupon = Coupon.builder()
                .couponType(CouponType.FIRST_COME_FIRST_SERVED)
                .title("선착순 테스트 쿠폰")
                .totalQuantity(100)
                .issuedQuantity(0)
                .dateIssueStart(LocalDateTime.now().minusDays(1))
                .dateIssueEnd(LocalDateTime.now().plusDays(1))
                .build();
        couponJpaRepository.save(coupon);

        CouponIssue couponIssue = CouponIssue.builder()
                .couponId(coupon.getId())
                .userId(1L)
                .build();
        couponIssueJpaRepository.save(couponIssue);

        CouponIssueException exception = assertThrows(CouponIssueException.class, () -> sut.issue(coupon.getId(), userId));
        assertEquals(exception.getErrorCode(), ErrorCode.DUPLICATED_COUPON_ISSUE);
    }

    @DisplayName("쿠폰이 존재하지 않는다면 예외를 반환한다.")
    @Test
    void issue_5() {
        long userId = 1L;
        long couponId = 1L;

        CouponIssueException exception = assertThrows(CouponIssueException.class, () -> sut.issue(couponId, userId));
        assertEquals(exception.getErrorCode(), ErrorCode.COUPON_NOT_EXIST);
    }
}
