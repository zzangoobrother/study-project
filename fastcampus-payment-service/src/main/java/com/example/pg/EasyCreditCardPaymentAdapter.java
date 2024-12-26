package com.example.pg;

import org.springframework.stereotype.Service;

@Service
public class EasyCreditCardPaymentAdapter implements CreditCardPaymentAdapter {
    @Override
    public Long processCreditPayment(Long amountKRW, String creditCardNumber) {
        return Math.round(Math.random() * 100000000);
    }
}
