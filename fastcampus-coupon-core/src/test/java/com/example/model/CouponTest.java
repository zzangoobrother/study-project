package com.example.model;

import com.example.exception.CouponIssueException;
import com.example.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CouponTest {

    @Test
    @DisplayName("발급 수량이 남아있다면 true를 반환한다.")
    void availableIssueQuantity_1() {
        Coupon coupon = Coupon.builder()
                .totalQuantity(100)
                .issuedQuantity(99)
                .build();

        boolean result = coupon.availableIssueQuantity();

        assertTrue(result);
    }

    @Test
    @DisplayName("발급 수량이 소진되었다면 false를 반환한다.")
    void availableIssueQuantity_2() {
        Coupon coupon = Coupon.builder()
                .totalQuantity(100)
                .issuedQuantity(100)
                .build();

        boolean result = coupon.availableIssueQuantity();

        assertFalse(result);
    }

    @Test
    @DisplayName("최대 발급 수량이 설정되지 않았다면 true를 반환한다.")
    void availableIssueQuantity_3() {
        Coupon coupon = Coupon.builder()
                .totalQuantity(null)
                .issuedQuantity(100)
                .build();

        boolean result = coupon.availableIssueQuantity();

        assertTrue(result);
    }

    @Test
    @DisplayName("발급 기간이 시작되지 않았다면 false를 반환한다.")
    void availableIssueDate_1() {
        Coupon coupon = Coupon.builder()
                .dateIssueStart(LocalDateTime.now().plusDays(1))
                .dateIssueEnd(LocalDateTime.now().plusDays(2))
                .build();

        boolean result = coupon.availableIssueDate();

        assertFalse(result);
    }

    @Test
    @DisplayName("발급 기간이 해당되면 true를 반환한다.")
    void availableIssueDate_2() {
        Coupon coupon = Coupon.builder()
                .dateIssueStart(LocalDateTime.now().minusDays(1))
                .dateIssueEnd(LocalDateTime.now().plusDays(2))
                .build();

        boolean result = coupon.availableIssueDate();

        assertTrue(result);
    }

    @Test
    @DisplayName("발급 기간이 종료되면 false를 반환한다.")
    void availableIssueDate_3() {
        Coupon coupon = Coupon.builder()
                .dateIssueStart(LocalDateTime.now().minusDays(2))
                .dateIssueEnd(LocalDateTime.now().minusDays(1))
                .build();

        boolean result = coupon.availableIssueDate();

        assertFalse(result);
    }

    @Test
    @DisplayName("발급 수량과 발급 기간이 유효하다면 발급에 성공한다.")
    void issue_1() {
        Coupon coupon = Coupon.builder()
                .totalQuantity(100)
                .issuedQuantity(99)
                .dateIssueStart(LocalDateTime.now().minusDays(1))
                .dateIssueEnd(LocalDateTime.now().plusDays(2))
                .build();

        coupon.issue();

        assertEquals(coupon.getIssuedQuantity(), 100);
    }

    @Test
    @DisplayName("발급 수량을 초과하면 예외를 반환한다.")
    void issue_2() {
        Coupon coupon = Coupon.builder()
                .totalQuantity(100)
                .issuedQuantity(100)
                .dateIssueStart(LocalDateTime.now().minusDays(1))
                .dateIssueEnd(LocalDateTime.now().plusDays(2))
                .build();

        CouponIssueException exception = assertThrows(CouponIssueException.class, coupon::issue);
        assertEquals(exception.getErrorCode(), ErrorCode.INVALID_COUPON_ISSUE_QUANTITY);
    }

    @Test
    @DisplayName("발급 기간이 아니면 예외를 반환한다.")
    void issue_3() {
        Coupon coupon = Coupon.builder()
                .totalQuantity(100)
                .issuedQuantity(99)
                .dateIssueStart(LocalDateTime.now().plusDays(1))
                .dateIssueEnd(LocalDateTime.now().plusDays(2))
                .build();

        CouponIssueException exception = assertThrows(CouponIssueException.class, coupon::issue);
        assertEquals(exception.getErrorCode(), ErrorCode.INVALID_COUPON_ISSUE_DATE);
    }

    @Test
    @DisplayName("발급 기간이 종료되면 true를 반환한다.")
    void isIssueComplete_1() {
        Coupon coupon = Coupon.builder()
                .totalQuantity(100)
                .issuedQuantity(99)
                .dateIssueStart(LocalDateTime.now().minusDays(2))
                .dateIssueEnd(LocalDateTime.now().minusDays(1))
                .build();

        boolean result = coupon.isIssueComplete();

        assertTrue(result);
    }

    @Test
    @DisplayName("잔여 발급 가능 수량이 없다면 true를 반환한다.")
    void isIssueComplete_2() {
        Coupon coupon = Coupon.builder()
                .totalQuantity(100)
                .issuedQuantity(100)
                .dateIssueStart(LocalDateTime.now().minusDays(2))
                .dateIssueEnd(LocalDateTime.now().plusDays(1))
                .build();

        boolean result = coupon.isIssueComplete();

        assertTrue(result);
    }

    @Test
    @DisplayName("발급 기한과 수량이 유효하면 false를 반환한다.")
    void isIssueComplete_3() {
        Coupon coupon = Coupon.builder()
                .totalQuantity(100)
                .issuedQuantity(0)
                .dateIssueStart(LocalDateTime.now().minusDays(2))
                .dateIssueEnd(LocalDateTime.now().plusDays(1))
                .build();

        boolean result = coupon.isIssueComplete();

        assertFalse(result);
    }
}
