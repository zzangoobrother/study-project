package com.example.entity;

import com.example.enums.PaymentMethodType;
import com.example.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(indexes = {@Index(name = "idx_userId", columnList = "userId")})
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(unique = true)
    private Long orderId;

    private Long amountKRW;

    private PaymentMethodType paymentMethodType;

    private String paymentData;

    private PaymentStatus paymentStatus;

    @Column(unique = true)
    private Long referenceCode;

    public Payment(Long userId, Long orderId, Long amountKRW, PaymentMethodType paymentMethodType, String paymentData, PaymentStatus paymentStatus, Long referenceCode) {
        this.userId = userId;
        this.orderId = orderId;
        this.amountKRW = amountKRW;
        this.paymentMethodType = paymentMethodType;
        this.paymentData = paymentData;
        this.paymentStatus = paymentStatus;
        this.referenceCode = referenceCode;
    }
}
