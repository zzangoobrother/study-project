package com.example.entity;

import com.example.enums.PaymentMethodType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(indexes = {@Index(name = "idx_userId", columnList = "userId")})
public class PaymentMethod {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private PaymentMethodType paymentMethodType;

    private String creditCardNumber;

    public PaymentMethod(Long userId, PaymentMethodType paymentMethodType, String creditCardNumber) {
        this.userId = userId;
        this.paymentMethodType = paymentMethodType;
        this.creditCardNumber = creditCardNumber;
    }
}
